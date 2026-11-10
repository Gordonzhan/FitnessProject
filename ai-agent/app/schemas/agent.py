from __future__ import annotations

from typing import Literal

from pydantic import Field

from app.rag.models import KnowledgeCitation
from app.schemas.base import ApiModel


class AgentMessage(ApiModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class FitnessAgentRequest(ApiModel):
    request_id: str = Field(min_length=1, max_length=100)
    session_id: str = Field(min_length=1, max_length=64)
    user_context: str = Field(min_length=20, max_length=2048)
    messages: list[AgentMessage] = Field(min_length=1, max_length=20)


class FitnessAgentResponse(ApiModel):
    request_id: str
    reply: str = Field(min_length=1, max_length=4000)
    provider: str
    tool_rounds: int = Field(ge=0, le=6)
    tools_used: list[str] = Field(default_factory=list, max_length=12)
    action: Literal["NONE", "WORKOUT_DRAFT"] = "NONE"
    workout_request: str | None = Field(default=None, max_length=500)
    safety_notice: bool = False
    citations: list[KnowledgeCitation] = Field(default_factory=list, max_length=10)


class ToolCall(ApiModel):
    name: str = Field(min_length=1, max_length=80)
    arguments: dict[str, object] = Field(default_factory=dict)


class AgentModelTurn(ApiModel):
    reply: str | None = Field(default=None, max_length=4000)
    tool_calls: list[ToolCall] = Field(default_factory=list, max_length=4)
    action: Literal["NONE", "WORKOUT_DRAFT"] = "NONE"
    workout_request: str | None = Field(default=None, max_length=500)
