from __future__ import annotations

from typing import Any, Literal, cast

import httpx
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.rag.models import KnowledgeSearchRequest, RiskLevel
from app.rag.retriever import HybridKnowledgeRetriever


class _Args(BaseModel):
    model_config = ConfigDict(extra="forbid")


class EmptyArgs(_Args):
    pass


class DaysArgs(_Args):
    days: Literal[7, 30] = 7


class SearchArgs(_Args):
    keyword: str = Field(min_length=1, max_length=50)
    limit: int = Field(default=5, ge=1, le=10)


class KnowledgeSearchArgs(_Args):
    query: str = Field(min_length=2, max_length=300)
    top_k: int = Field(default=5, ge=1, le=10)
    topics: list[str] = Field(default_factory=list, max_length=5)
    versions: list[str] = Field(default_factory=list, max_length=5)
    language: str = Field(default="zh-CN", pattern=r"^[a-z]{2}(?:-[A-Z]{2})?$")
    max_risk_level: RiskLevel = RiskLevel.MODERATE


TOOL_MODELS: dict[str, type[_Args]] = {
    "get_user_profile": EmptyArgs,
    "get_training_preferences": EmptyArgs,
    "get_recent_analysis": DaysArgs,
    "get_today_nutrition": EmptyArgs,
    "get_recent_workouts": DaysArgs,
    "search_foods": SearchArgs,
    "search_exercises": SearchArgs,
    "search_fitness_knowledge": KnowledgeSearchArgs,
}


TOOL_DEFINITIONS: list[dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": model.model_json_schema(),
        },
    }
    for name, description, model in (
        ("get_user_profile", "读取当前用户的身体档案与健身目标", EmptyArgs),
        ("get_training_preferences", "读取当前用户的训练偏好、器械和限制", EmptyArgs),
        ("get_recent_analysis", "读取当前用户近7或30天训练、饮食和体重趋势汇总", DaysArgs),
        ("get_today_nutrition", "读取当前用户今日实际饮食和目标差值", EmptyArgs),
        ("get_recent_workouts", "读取当前用户近7或30天训练记录", DaysArgs),
        ("search_foods", "搜索权威食材目录，不能用于猜测用户已吃的食物", SearchArgs),
        ("search_exercises", "搜索启用的训练动作目录", SearchArgs),
        (
            "search_fitness_knowledge",
            "检索已审核的通用健身知识；不含用户资料，结果不足时必须明确拒答",
            KnowledgeSearchArgs,
        ),
    )
]


class JavaFitnessTools:
    """只通过 Java 内部 API 读取当前签名用户的数据，不接触数据库。"""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
        knowledge: HybridKnowledgeRetriever | None = None,
    ) -> None:
        self._base_url = settings.java_internal_base_url.rstrip("/")
        self._token = (
            settings.java_service_token.get_secret_value()
            or settings.service_token.get_secret_value()
        )
        self._timeout = settings.tool_timeout_seconds
        self._transport = transport
        self._knowledge = knowledge

    async def call(
        self, name: str, arguments: dict[str, object], user_context: str, request_id: str
    ) -> dict[str, Any]:
        model = TOOL_MODELS.get(name)
        if model is None:
            raise AgentServiceError(422, "AGENT_TOOL_UNKNOWN", "模型请求了未授权的工具")
        try:
            args = model.model_validate(arguments)
        except ValidationError as error:
            raise AgentServiceError(422, "AGENT_TOOL_ARGUMENT_INVALID", "工具参数不合法") from error
        if name == "search_fitness_knowledge":
            if self._knowledge is None:
                raise AgentServiceError(503, "KNOWLEDGE_DISABLED", "健身知识库尚未启用")
            search = cast(KnowledgeSearchArgs, args)
            result = await self._knowledge.search(
                KnowledgeSearchRequest(
                    query=search.query,
                    top_k=search.top_k,
                    topics=search.topics,
                    versions=search.versions,
                    language=search.language,
                    max_risk_level=search.max_risk_level,
                )
            )
            return result.model_dump(mode="json", by_alias=True)
        path, method, payload = self._request(name, args)
        headers = {"X-Agent-User-Context": user_context, "X-Request-Id": request_id}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                kwargs: dict[str, Any] = {"headers": headers, "timeout": self._timeout}
                if payload is not None:
                    kwargs["json"] = payload
                response = await client.request(method, f"{self._base_url}{path}", **kwargs)
        except httpx.TimeoutException as error:
            raise AgentServiceError(
                504, "AGENT_TOOL_TIMEOUT", "读取健身数据超时，请稍后重试"
            ) from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503, "AGENT_TOOL_UNAVAILABLE", "暂时无法读取健身数据"
            ) from error
        if response.status_code in {401, 403}:
            raise AgentServiceError(403, "AGENT_TOOL_AUTH_REJECTED", "健身数据授权已失效，请重试")
        if response.status_code >= 400:
            raise AgentServiceError(502, "AGENT_TOOL_FAILED", "健身数据查询失败，请稍后重试")
        try:
            body = response.json()
            if body.get("code") != 200:
                raise ValueError("business response failed")
            data = body.get("data")
            return data if isinstance(data, dict) else {"items": data}
        except (ValueError, TypeError) as error:
            raise AgentServiceError(
                502, "AGENT_TOOL_RESPONSE_INVALID", "健身数据响应无法解析"
            ) from error

    @staticmethod
    def _request(name: str, args: _Args) -> tuple[str, str, dict[str, object] | None]:
        if name == "get_user_profile":
            return "/internal/ai/context/profile", "GET", None
        if name == "get_training_preferences":
            return "/internal/ai/context/preferences", "GET", None
        if name == "get_recent_analysis":
            return f"/internal/ai/context/analysis?days={args.days}", "GET", None  # type: ignore[attr-defined]
        if name == "get_today_nutrition":
            return "/internal/ai/context/nutrition/today", "GET", None
        if name == "get_recent_workouts":
            return f"/internal/ai/context/workouts/recent?days={args.days}", "GET", None  # type: ignore[attr-defined]
        suffix = "foods" if name == "search_foods" else "exercises"
        search = cast(SearchArgs, args)
        return (
            f"/internal/ai/tools/{suffix}/search",
            "POST",
            {
                "keyword": search.keyword,
                "limit": search.limit,
            },
        )
