from __future__ import annotations

from pathlib import Path

import httpx
import pytest

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.rag.factory import build_knowledge_retriever
from app.tools.fitness import JavaFitnessTools

CORPUS = Path(__file__).resolve().parents[1] / "knowledge" / "documents"


def tools(transport: httpx.AsyncBaseTransport | None = None) -> JavaFitnessTools:
    return JavaFitnessTools(
        Settings(
            environment="test",
            provider="fake",
            service_token="",
            java_service_token="test-java-token",
            java_internal_base_url="http://java.test/api",
        ),
        transport,
    )


@pytest.mark.asyncio
async def test_unknown_tool_and_invalid_arguments_are_rejected_before_http() -> None:
    client = tools()

    with pytest.raises(AgentServiceError) as unknown:
        await client.call("delete_workout", {}, "signed-context", "request-1")
    with pytest.raises(AgentServiceError) as invalid:
        await client.call("get_recent_analysis", {"days": 14}, "signed-context", "request-2")

    assert unknown.value.code == "AGENT_TOOL_UNKNOWN"
    assert invalid.value.code == "AGENT_TOOL_ARGUMENT_INVALID"


@pytest.mark.asyncio
async def test_tool_timeout_returns_stable_understandable_error() -> None:
    def timeout(_: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("private upstream detail")

    client = tools(httpx.MockTransport(timeout))

    with pytest.raises(AgentServiceError) as captured:
        await client.call("get_user_profile", {}, "signed-context", "request-3")

    assert captured.value.code == "AGENT_TOOL_TIMEOUT"
    assert captured.value.message == "读取健身数据超时，请稍后重试"
    assert "private upstream detail" not in captured.value.message


@pytest.mark.asyncio
async def test_tool_forwards_only_signed_context_and_service_identity() -> None:
    captured: dict[str, str | None] = {}

    def success(request: httpx.Request) -> httpx.Response:
        captured["context"] = request.headers.get("X-Agent-User-Context")
        captured["authorization"] = request.headers.get("Authorization")
        captured["url"] = str(request.url)
        return httpx.Response(
            200,
            json={"code": 200, "message": "success", "data": {"goal": "FAT_LOSS"}},
        )

    client = tools(httpx.MockTransport(success))
    result = await client.call("get_user_profile", {}, "signed-context", "request-4")

    assert result == {"goal": "FAT_LOSS"}
    assert captured == {
        "context": "signed-context",
        "authorization": "Bearer test-java-token",
        "url": "http://java.test/api/internal/ai/context/profile",
    }


@pytest.mark.asyncio
async def test_knowledge_tool_is_local_read_only_and_accepts_no_user_id() -> None:
    settings = Settings(
        environment="local",
        knowledge_directory=str(CORPUS),
        embedding_provider="fake",
        embedding_dimension=128,
    )
    retriever = build_knowledge_retriever(settings, memory=True)
    await retriever.index(rebuild=True)
    client = JavaFitnessTools(settings, knowledge=retriever)

    result = await client.call(
        "search_fitness_knowledge",
        {"query": "组间休息多久", "top_k": 3},
        "this-context-must-not-be-used",
        "request-knowledge-1",
    )

    assert result["sufficient"] is True
    assert result["results"]
    with pytest.raises(AgentServiceError) as captured:
        await client.call(
            "search_fitness_knowledge",
            {"query": "组间休息", "userId": 7},
            "context",
            "request-knowledge-2",
        )
    assert captured.value.code == "AGENT_TOOL_ARGUMENT_INVALID"
