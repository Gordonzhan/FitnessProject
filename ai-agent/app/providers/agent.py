from __future__ import annotations

import json
import re
from typing import Any, Protocol

import httpx
from pydantic import BaseModel, ConfigDict

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.core.logging import logger
from app.schemas.agent import AgentModelTurn, ToolCall


class FitnessAgentProvider(Protocol):
    @property
    def name(self) -> str: ...

    async def next_turn(
        self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]
    ) -> AgentModelTurn: ...


class FakeFitnessAgentProvider:
    """测试 Provider 也走完整 Tool Loop，确保不使用编造的用户数据。"""

    @property
    def name(self) -> str:
        return "fake"

    async def next_turn(
        self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]
    ) -> AgentModelTurn:
        used = {item.get("name") for item in messages if item.get("role") == "tool"}
        available = {
            str(item.get("function", {}).get("name"))
            for item in tools
            if isinstance(item.get("function"), dict)
        }
        user_text = next(
            (
                str(item.get("content", ""))
                for item in reversed(messages)
                if item.get("role") == "user"
            ),
            "",
        )
        wanted = [
            "get_user_profile",
            "get_training_preferences",
            "get_today_nutrition",
            "get_recent_analysis",
            "get_recent_workouts",
        ]
        pending = [name for name in wanted if name in available and name not in used]
        if pending:
            name = pending[0]
            args: dict[str, object] = (
                {"days": 7} if name in {"get_recent_analysis", "get_recent_workouts"} else {}
            )
            return AgentModelTurn(tool_calls=[ToolCall(name=name, arguments=args)])
        tool_data: dict[str, Any] = {}
        for item in messages:
            if item.get("role") != "tool":
                continue
            try:
                tool_data[str(item.get("name"))] = json.loads(str(item.get("content", "{}")))
            except ValueError:
                continue
        profile = tool_data.get("get_user_profile", {})
        nutrition = tool_data.get("get_today_nutrition", {})
        analysis = tool_data.get("get_recent_analysis", {})
        calorie_text = (
            f"今日已记录 {nutrition.get('calories')} 千卡，"
            f"剩余约 {nutrition.get('remainingCalories')} 千卡"
            if nutrition.get("recorded")
            else "今天还没有完整饮食记录"
        )
        goal = profile.get("goal", "未设置目标")
        state = analysis.get("dataState", "数据状态未知")
        action = (
            "WORKOUT_DRAFT"
            if any(key in user_text for key in ("训练计划", "训练草稿", "帮我练", "安排训练"))
            else "NONE"
        )
        return AgentModelTurn(
            reply=(
                f"我已读取当前记录：目标是 {goal}；{calorie_text}；"
                f"近7天数据状态为 {state}。建议按已记录的剩余额度安排均衡饮食，"
                "并结合近期训练完成情况循序渐进；缺失记录不会被当作零摄入。"
            ),
            action=action,
            workout_request=user_text[-500:] if action == "WORKOUT_DRAFT" else None,
        )


class _OllamaMessage(BaseModel):
    model_config = ConfigDict(extra="ignore")
    content: str = ""
    tool_calls: list[dict[str, Any]] = []


class _OllamaResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")
    message: _OllamaMessage
    load_duration: int = 0
    prompt_eval_count: int = 0
    prompt_eval_duration: int = 0
    eval_count: int = 0
    eval_duration: int = 0


class OllamaFitnessAgentProvider:
    def __init__(
        self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None
    ) -> None:
        self._url = settings.ollama_base_url.rstrip("/") + "/api/chat"
        self._model = settings.ollama_agent_model
        self._timeout = settings.ollama_agent_timeout_seconds
        self._keep_alive = settings.ollama_agent_keep_alive
        self._transport = transport

    @property
    def name(self) -> str:
        return "ollama"

    async def next_turn(
        self, messages: list[dict[str, Any]], tools: list[dict[str, Any]]
    ) -> AgentModelTurn:
        payload = {
            "model": self._model,
            "stream": False,
            "think": False,
            "tools": tools,
            "format": AgentModelTurn.model_json_schema(by_alias=True),
            "messages": messages,
            "options": {"temperature": 0.2},
            "keep_alive": self._keep_alive,
        }
        input_characters = len(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                response = await client.post(self._url, json=payload, timeout=self._timeout)
        except httpx.TimeoutException as error:
            raise AgentServiceError(
                504, "FITNESS_AGENT_TIMEOUT", "健身助手响应超时，请稍后重试"
            ) from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503, "FITNESS_AGENT_UNAVAILABLE", "健身助手暂时不可用"
            ) from error
        if response.status_code >= 400:
            raise AgentServiceError(502, "FITNESS_AGENT_REQUEST_FAILED", "健身助手模型调用失败")
        try:
            parsed_response = _OllamaResponse.model_validate(response.json())
            logger.info(
                "ollama_agent_metrics",
                extra={
                    "provider": self.name,
                    "modelInputCharacters": input_characters,
                    "modelMessageCount": len(messages),
                    "toolSchemaCount": len(tools),
                    "loadDurationMs": _nanoseconds_to_milliseconds(parsed_response.load_duration),
                    "promptEvalDurationMs": _nanoseconds_to_milliseconds(
                        parsed_response.prompt_eval_duration
                    ),
                    "promptEvalCount": parsed_response.prompt_eval_count,
                    "evalDurationMs": _nanoseconds_to_milliseconds(
                        parsed_response.eval_duration
                    ),
                    "evalCount": parsed_response.eval_count,
                },
            )
            message = parsed_response.message
            calls = []
            for raw in message.tool_calls:
                function = raw.get("function", {})
                arguments = function.get("arguments", {})
                if isinstance(arguments, str):
                    arguments = json.loads(arguments)
                calls.append(ToolCall(name=function.get("name", ""), arguments=arguments))
            if calls:
                return AgentModelTurn(tool_calls=calls)
            return self._parse_final_turn(message.content, messages)
        except (ValueError, TypeError) as error:
            raise AgentServiceError(
                502, "FITNESS_AGENT_RESPONSE_INVALID", "健身助手返回内容无法校验"
            ) from error

    @staticmethod
    def _parse_final_turn(content: str, messages: list[dict[str, Any]]) -> AgentModelTurn:
        """兼容模型偶发的 Markdown 围栏和对象型 workoutRequest，同时保持强类型校验。"""

        normalized = re.sub(r"^\s*```(?:json)?\s*|\s*```\s*$", "", content, flags=re.IGNORECASE)
        start = normalized.find("{")
        if start < 0:
            raise ValueError("agent response does not contain JSON")
        parsed, _ = json.JSONDecoder().raw_decode(normalized[start:])
        if not isinstance(parsed, dict):
            raise ValueError("agent response is not an object")

        action = str(parsed.get("action", "NONE")).upper()
        parsed["action"] = action
        workout_request = parsed.get("workoutRequest", parsed.get("workout_request"))
        if isinstance(workout_request, (dict, list)):
            workout_request = json.dumps(workout_request, ensure_ascii=False, separators=(",", ":"))
        if action == "WORKOUT_DRAFT" and not workout_request:
            workout_request = next(
                (
                    str(item.get("content", ""))
                    for item in reversed(messages)
                    if item.get("role") == "user"
                ),
                "",
            )
        if workout_request:
            parsed["workoutRequest"] = str(workout_request)[:500]
            parsed.pop("workout_request", None)
        if action == "WORKOUT_DRAFT" and not parsed.get("reply"):
            parsed["reply"] = "已根据当前对话生成训练草稿，请在页面检查并确认后再保存。"
        return AgentModelTurn.model_validate(parsed)


def build_fitness_agent_provider(settings: Settings) -> FitnessAgentProvider:
    provider = settings.chat_provider or settings.provider
    if provider == "fake":
        return FakeFitnessAgentProvider()
    if provider == "ollama":
        return OllamaFitnessAgentProvider(settings)
    raise ValueError(f"Unsupported fitness agent provider: {provider}")


def _nanoseconds_to_milliseconds(value: int) -> float:
    return round(value / 1_000_000, 2)
