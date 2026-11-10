from __future__ import annotations

import json
from typing import Any

import pytest

from app.api.errors import AgentServiceError
from app.providers.agent import FakeFitnessAgentProvider
from app.schemas.agent import AgentMessage, AgentModelTurn, FitnessAgentRequest, ToolCall
from app.workflows.fitness_agent import FitnessAgentWorkflow


class StubTools:
    def __init__(self) -> None:
        self.calls: list[str] = []
        self.arguments: list[dict[str, object]] = []

    async def call(
        self, name: str, arguments: dict[str, object], user_context: str, request_id: str
    ) -> dict[str, Any]:
        assert user_context == "signed-current-user-context"
        self.calls.append(name)
        self.arguments.append(arguments)
        results: dict[str, dict[str, Any]] = {
            "get_user_profile": {"goal": "lose_weight"},
            "get_today_nutrition": {
                "recorded": True,
                "calories": 900,
                "remainingCalories": 700,
            },
            "get_recent_analysis": {"dataState": "PARTIAL"},
            "search_fitness_knowledge": {
                "sufficient": True,
                "message": "只依据已审核内容回答",
                "results": [{
                    "documentId": "KB_TEST",
                    "title": "测试知识",
                    "content": "循序渐进。" * 200,
                    "sourceUrl": "https://example.com/source",
                    "sourceName": "Official Source",
                    "version": "v1",
                    "relevanceScore": 0.9,
                }],
                "citations": [{
                    "documentId": "KB_TEST",
                    "title": "测试知识",
                    "sourceName": "Official Source",
                    "sourceUrl": "https://example.com/source",
                    "version": "v1",
                }],
            },
        }
        return results.get(name, {"items": []})


def request(content: str) -> FitnessAgentRequest:
    return FitnessAgentRequest(
        request_id="agent-test-001",
        session_id="00000000-0000-0000-0000-000000000001",
        user_context="signed-current-user-context",
        messages=[AgentMessage(role="user", content=content)],
    )


@pytest.mark.asyncio
async def test_tool_loop_uses_real_tool_results_and_requests_workout_draft() -> None:
    tools = StubTools()
    workflow = FitnessAgentWorkflow(FakeFitnessAgentProvider(), tools, 6)  # type: ignore[arg-type]

    response = await workflow.run(request("结合记录帮我生成训练草稿"))

    assert response.action == "WORKOUT_DRAFT"
    assert response.tool_rounds == 0
    assert response.tools_used == tools.calls
    assert response.tools_used == [
        "get_user_profile",
        "get_recent_analysis",
        "get_training_preferences",
        "get_recent_workouts",
    ]
    assert "lose_weight" in response.reply


@pytest.mark.asyncio
async def test_high_risk_message_is_blocked_before_any_tool_call() -> None:
    tools = StubTools()
    workflow = FitnessAgentWorkflow(FakeFitnessAgentProvider(), tools, 6)  # type: ignore[arg-type]

    response = await workflow.run(request("训练时胸痛和呼吸困难怎么办"))

    assert response.safety_notice is True
    assert response.provider == "guardrail"
    assert tools.calls == []
    assert "停止训练" in response.reply


@pytest.mark.asyncio
async def test_general_knowledge_query_returns_structured_deduplicated_citations() -> None:
    tools = StubTools()
    workflow = FitnessAgentWorkflow(FakeFitnessAgentProvider(), tools, 6)  # type: ignore[arg-type]

    response = await workflow.run(request("力量训练组间休息为什么重要？"))

    assert "search_fitness_knowledge" in response.tools_used
    assert response.tools_used == ["search_fitness_knowledge"]
    assert tools.arguments == [{
        "query": "力量训练组间休息为什么重要？",
        "top_k": 3,
        "topics": ["rest_intervals"],
    }]
    assert len(response.citations) == 1
    assert response.citations[0].document_id == "KB_TEST"


class CapturingProvider:
    name = "capture"

    def __init__(self) -> None:
        self.messages: list[dict[str, Any]] = []
        self.definitions: list[dict[str, Any]] = []

    async def next_turn(
        self, messages: list[dict[str, Any]], definitions: list[dict[str, Any]]
    ) -> AgentModelTurn:
        self.messages = messages
        self.definitions = definitions
        return AgentModelTurn(reply="已依据知识库回答。")


@pytest.mark.asyncio
async def test_general_knowledge_context_is_compact_and_has_no_user_tools_or_source_url() -> None:
    tools = StubTools()
    provider = CapturingProvider()
    workflow = FitnessAgentWorkflow(provider, tools, 6)  # type: ignore[arg-type]

    await workflow.run(request("力量训练组间休息为什么重要？"))

    assert tools.calls == ["search_fitness_knowledge"]
    assert provider.definitions == []
    tool_payload = next(
        item["content"] for item in provider.messages if item.get("role") == "tool"
    )
    parsed = json.loads(tool_payload)
    assert len(parsed["results"][0]["content"]) == 400
    assert "sourceUrl" not in parsed["results"][0]
    assert "citations" not in parsed


@pytest.mark.asyncio
async def test_specific_exercise_question_prefetches_rag_and_catalog_lookup() -> None:
    tools = StubTools()
    provider = CapturingProvider()
    workflow = FitnessAgentWorkflow(provider, tools, 6)  # type: ignore[arg-type]

    await workflow.run(request("深蹲怎么做，有哪些常见错误？"))

    assert tools.calls == ["search_exercises", "search_fitness_knowledge"]
    assert tools.arguments == [
        {"keyword": "深蹲", "limit": 3},
        {
            "query": "深蹲怎么做，有哪些常见错误？",
            "top_k": 3,
            "topics": ["movement_squat"],
        },
    ]
    assert provider.definitions == []


def test_exercise_catalog_result_is_bounded_before_entering_model_context() -> None:
    result = FitnessAgentWorkflow._compact_tool_result(
        "search_exercises",
        {
            "items": [
                {
                    "exerciseCode": f"SQUAT_{index}",
                    "instructions": "动作说明" * 200,
                    "safetyCues": "安全提示" * 200,
                    "commonMistakes": "常见错误" * 200,
                }
                for index in range(5)
            ]
        },
    )

    assert len(result["items"]) == 3
    assert len(result["items"][0]["instructions"]) == 400
    assert len(result["items"][0]["safetyCues"]) == 300
    assert len(result["items"][0]["commonMistakes"]) == 300


class LoopingProvider:
    name = "looping"

    async def next_turn(
        self, messages: list[dict[str, Any]], definitions: list[dict[str, Any]]
    ) -> AgentModelTurn:
        return AgentModelTurn(
            tool_calls=[ToolCall(name="get_recent_analysis", arguments={"days": 7})]
        )


class IncorrectDraftProvider:
    name = "incorrect-draft"

    async def next_turn(
        self, messages: list[dict[str, Any]], definitions: list[dict[str, Any]]
    ) -> AgentModelTurn:
        return AgentModelTurn(
            reply="已生成训练草稿，请查看并编辑草稿",
            action="WORKOUT_DRAFT",
            workout_request="今天生成力量训练",
        )


@pytest.mark.asyncio
async def test_advice_request_deterministically_rejects_model_draft_action() -> None:
    tools = StubTools()
    workflow = FitnessAgentWorkflow(IncorrectDraftProvider(), tools, 6)  # type: ignore[arg-type]

    response = await workflow.run(request("结合近期数据给我建议"))

    assert response.action == "NONE"
    assert response.workout_request is None
    assert "没有创建或保存训练草稿" in response.reply


@pytest.mark.asyncio
async def test_tool_loop_stops_at_configured_round_limit() -> None:
    tools = StubTools()
    workflow = FitnessAgentWorkflow(LoopingProvider(), tools, 1)  # type: ignore[arg-type]

    with pytest.raises(AgentServiceError) as captured:
        await workflow.run(request("分析我近7天的训练"))

    assert captured.value.code == "FITNESS_AGENT_TOOL_LIMIT"
    assert captured.value.message == "查询步骤过多，请缩小问题范围后重试"
