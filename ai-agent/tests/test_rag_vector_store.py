from __future__ import annotations

import json
from typing import Any

import httpx
import pytest

from app.api.errors import AgentServiceError
from app.rag.models import RiskLevel
from app.rag.vector_store import QdrantVectorStore


@pytest.mark.asyncio
async def test_qdrant_collection_check_is_idempotent() -> None:
    calls: list[str] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        calls.append(f"{request.method} {request.url.path}")
        return httpx.Response(
            200,
            json={"result": {"config": {"params": {"vectors": {"size": 64}}}}},
        )

    store = QdrantVectorStore(
        "http://qdrant", "fitness_knowledge_local_v1", "", 1,
        httpx.MockTransport(handler),
    )

    await store.ensure_collection(64)

    assert calls == ["GET /collections/fitness_knowledge_local_v1"]


@pytest.mark.asyncio
async def test_qdrant_rebuild_refuses_non_local_collection() -> None:
    async def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"result": {"config": {"params": {"vectors": {"size": 32}}}}},
        )

    store = QdrantVectorStore(
        "http://qdrant", "production_knowledge", "", 1,
        httpx.MockTransport(handler),
    )

    with pytest.raises(AgentServiceError) as captured:
        await store.ensure_collection(64, rebuild=True)

    assert captured.value.code == "QDRANT_REBUILD_FORBIDDEN"


@pytest.mark.asyncio
async def test_qdrant_search_sends_all_metadata_filters() -> None:
    captured: dict[str, Any] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        captured.update(json.loads(request.content))
        return httpx.Response(200, json={"result": []})

    store = QdrantVectorStore(
        "http://qdrant", "fitness_knowledge_local_v1", "", 1,
        httpx.MockTransport(handler),
    )

    await store.search(
        [0.0] * 64,
        5,
        ["frequency"],
        ["2018-2ed-project-summary-v1"],
        "zh-CN",
        RiskLevel.MODERATE,
    )

    keys = {item["key"] for item in captured["filter"]["must"]}
    assert keys == {"reviewStatus", "topics", "language", "version", "riskLevel"}
