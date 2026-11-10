from __future__ import annotations

import time
from typing import Any

import httpx
from pydantic import Field, ValidationError

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.core.logging import logger
from app.schemas.base import ApiModel


class ExerciseCatalogItem(ApiModel):
    exercise_code: str = Field(min_length=3, max_length=32)
    name: str = Field(min_length=1, max_length=50)
    category: str = Field(min_length=1, max_length=30)
    primary_muscles: str = Field(min_length=1, max_length=100)
    equipment: str = Field(min_length=1, max_length=50)
    intensity: str = Field(min_length=1, max_length=20)
    instructions: str = ""
    safety_cues: str = ""
    common_mistakes: str = ""
    limitation_tags: list[str] = Field(default_factory=list)
    alternative_exercise_codes: list[str] = Field(default_factory=list)
    default_sets: int | None = Field(default=None, ge=1, le=10)
    default_reps: int | None = Field(default=None, ge=1, le=100)
    default_rest_seconds: int | None = Field(default=None, ge=0, le=600)
    default_rpe: int | None = Field(default=None, ge=1, le=10)
    tempo: str = ""


class ExerciseCatalogSearchResponse(ApiModel):
    items: list[ExerciseCatalogItem] = Field(default_factory=list, max_length=10)


class PublicExerciseCatalogClient:
    """通过服务身份读取 Java 公共动作目录，不发送用户上下文。"""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = settings.java_internal_base_url.rstrip("/")
        self._token = (
            settings.java_service_token.get_secret_value()
            or settings.service_token.get_secret_value()
        )
        self._timeout = settings.tool_timeout_seconds
        self._transport = transport

    async def search(
        self, query: str, limit: int, request_id: str
    ) -> ExerciseCatalogSearchResponse:
        started = time.perf_counter()
        headers = {"X-Request-Id": request_id}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                response = await client.post(
                    f"{self._base_url}/internal/ai/public/tools/exercises/search",
                    headers=headers,
                    json={"keyword": query, "limit": limit},
                    timeout=self._timeout,
                )
        except httpx.TimeoutException as error:
            raise AgentServiceError(
                504, "MCP_CATALOG_TIMEOUT", "读取公共动作目录超时，请稍后重试"
            ) from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503, "MCP_CATALOG_UNAVAILABLE", "公共动作目录暂时不可用"
            ) from error
        if response.status_code in {401, 403}:
            raise AgentServiceError(
                403, "MCP_CATALOG_AUTH_REJECTED", "公共动作目录服务认证失败"
            )
        if response.status_code >= 400:
            raise AgentServiceError(502, "MCP_CATALOG_FAILED", "公共动作目录查询失败")
        try:
            body: Any = response.json()
            if not isinstance(body, dict) or body.get("code") != 200:
                raise ValueError("business response failed")
            result = ExerciseCatalogSearchResponse.model_validate(body.get("data"))
        except (ValueError, TypeError, ValidationError) as error:
            raise AgentServiceError(
                502, "MCP_CATALOG_RESPONSE_INVALID", "公共动作目录响应无法解析"
            ) from error
        logger.info(
            "mcp_exercise_catalog_completed",
            extra={
                "requestId": request_id,
                "limit": limit,
                "hitCount": len(result.items),
                "durationMs": round((time.perf_counter() - started) * 1000, 2),
            },
        )
        return result
