from __future__ import annotations

from typing import Literal

from app.schemas.base import ApiModel


class HealthResponse(ApiModel):
    """Agent 健康检查响应。"""

    status: Literal["UP"] = "UP"
    service: str = "fitness-ai-agent"
    version: str = "0.1.0"
    provider: str
