from __future__ import annotations

import math

import httpx
import pytest

from app.api.errors import AgentServiceError
from app.rag.embeddings import FakeEmbeddingProvider, OllamaEmbeddingProvider


@pytest.mark.asyncio
async def test_fake_embedding_is_deterministic_and_finite() -> None:
    provider = FakeEmbeddingProvider(32)
    first = await provider.embed(["力量训练", "蛋白质"])
    second = await provider.embed(["力量训练", "蛋白质"])

    assert first == second
    assert all(len(item) == 32 for item in first)
    assert all(math.isfinite(value) for item in first for value in item)


@pytest.mark.asyncio
async def test_ollama_embedding_rejects_dimension_mismatch() -> None:
    async def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"embeddings": [[0.1, 0.2]]})

    provider = OllamaEmbeddingProvider(
        "http://ollama", "embed", 3, 1, httpx.MockTransport(handler)
    )

    with pytest.raises(AgentServiceError) as captured:
        await provider.embed(["test"])

    assert captured.value.code == "EMBEDDING_DIMENSION_MISMATCH"
