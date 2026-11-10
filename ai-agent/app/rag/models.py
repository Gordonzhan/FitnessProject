from __future__ import annotations

from datetime import date
from enum import StrEnum

from pydantic import Field, HttpUrl, field_validator

from app.schemas.base import ApiModel


class ReviewStatus(StrEnum):
    CANDIDATE = "CANDIDATE"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class RiskLevel(StrEnum):
    LOW = "LOW"
    MODERATE = "MODERATE"
    HIGH = "HIGH"


class KnowledgeSection(ApiModel):
    heading: str = Field(min_length=1, max_length=160)
    content: str = Field(min_length=20, max_length=6000)


class KnowledgeDocument(ApiModel):
    document_id: str = Field(pattern=r"^[A-Z0-9][A-Z0-9_-]{2,63}$")
    title: str = Field(min_length=1, max_length=240)
    source_name: str = Field(min_length=1, max_length=240)
    source_url: HttpUrl
    published_or_updated_at: date
    version: str = Field(pattern=r"^[A-Za-z0-9._-]{1,40}$")
    topics: list[str] = Field(min_length=1, max_length=12)
    language: str = Field(pattern=r"^[a-z]{2}(?:-[A-Z]{2})?$")
    risk_level: RiskLevel
    review_status: ReviewStatus
    reviewed_at: date | None = None
    reviewer: str | None = Field(default=None, max_length=120)
    sections: list[KnowledgeSection] = Field(min_length=1, max_length=30)

    @field_validator("reviewed_at")
    @classmethod
    def approved_requires_review_date(cls, value: date | None, info: object) -> date | None:
        data = getattr(info, "data", {})
        if data.get("review_status") == ReviewStatus.APPROVED and value is None:
            raise ValueError("APPROVED document requires reviewedAt")
        return value


class KnowledgeChunk(ApiModel):
    chunk_id: str
    point_id: str
    document_id: str
    title: str
    content: str
    source_url: str
    source_name: str
    version: str
    topics: list[str]
    language: str
    risk_level: RiskLevel
    review_status: ReviewStatus

    def payload(self) -> dict[str, object]:
        return self.model_dump(mode="json", by_alias=True, exclude={"point_id"})


class KnowledgeSearchRequest(ApiModel):
    query: str = Field(min_length=2, max_length=300)
    top_k: int = Field(default=5, ge=1, le=10)
    topics: list[str] = Field(default_factory=list, max_length=5)
    versions: list[str] = Field(default_factory=list, max_length=5)
    language: str = Field(default="zh-CN", pattern=r"^[a-z]{2}(?:-[A-Z]{2})?$")
    max_risk_level: RiskLevel = RiskLevel.MODERATE


class KnowledgeSearchHit(ApiModel):
    chunk_id: str
    document_id: str
    title: str
    content: str
    source_url: str
    source_name: str
    version: str
    relevance_score: float = Field(ge=0, le=1)


class KnowledgeCitation(ApiModel):
    document_id: str
    title: str
    source_name: str
    source_url: str | None = None
    version: str


class KnowledgeSearchResponse(ApiModel):
    sufficient: bool
    message: str
    results: list[KnowledgeSearchHit] = Field(default_factory=list)
    citations: list[KnowledgeCitation] = Field(default_factory=list)
