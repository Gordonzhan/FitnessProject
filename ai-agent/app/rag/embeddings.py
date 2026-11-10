from __future__ import annotations

import hashlib
import math
from typing import Protocol

import httpx

from app.api.errors import AgentServiceError


class EmbeddingProvider(Protocol):
    @property
    def name(self) -> str: ...

    @property
    def dimension(self) -> int: ...

    async def embed(self, texts: list[str]) -> list[list[float]]: ...


def validate_embeddings(vectors: list[list[float]], expected: int, dimension: int) -> None:
    if len(vectors) != expected:
        raise AgentServiceError(502, "EMBEDDING_COUNT_MISMATCH", "Embedding 返回数量不一致")
    if any(len(vector) != dimension for vector in vectors):
        raise AgentServiceError(502, "EMBEDDING_DIMENSION_MISMATCH", "Embedding 向量维度不一致")
    if any(not math.isfinite(value) for vector in vectors for value in vector):
        raise AgentServiceError(502, "EMBEDDING_VALUE_INVALID", "Embedding 返回了非法数值")


class FakeEmbeddingProvider:
    """基于字符二元组的稳定哈希向量，只用于测试与离线评测。"""

    def __init__(self, dimension: int = 64) -> None:
        self._dimension = dimension

    @property
    def name(self) -> str:
        return "fake-hash-v1"

    @property
    def dimension(self) -> int:
        return self._dimension

    async def embed(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for text in texts:
            normalized = "".join(text.lower().split())
            tokens = [normalized[index : index + 2] for index in range(max(1, len(normalized) - 1))]
            vector = [0.0] * self._dimension
            for token in tokens:
                digest = hashlib.sha256(token.encode()).digest()
                index = int.from_bytes(digest[:4], "big") % self._dimension
                vector[index] += 1.0 if digest[4] % 2 == 0 else -1.0
            norm = math.sqrt(sum(value * value for value in vector)) or 1.0
            vectors.append([value / norm for value in vector])
        validate_embeddings(vectors, len(texts), self.dimension)
        return vectors


class OllamaEmbeddingProvider:
    def __init__(
        self,
        base_url: str,
        model: str,
        dimension: int,
        timeout: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._url = base_url.rstrip("/") + "/api/embed"
        self._model = model
        self._dimension = dimension
        self._timeout = timeout
        self._transport = transport

    @property
    def name(self) -> str:
        return self._model

    @property
    def dimension(self) -> int:
        return self._dimension

    async def embed(self, texts: list[str]) -> list[list[float]]:
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                response = await client.post(
                    self._url,
                    json={"model": self._model, "input": texts, "truncate": True},
                    timeout=self._timeout,
                )
        except httpx.TimeoutException as error:
            raise AgentServiceError(504, "EMBEDDING_TIMEOUT", "Embedding 生成超时") from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503, "EMBEDDING_UNAVAILABLE", "Embedding 服务暂时不可用"
            ) from error
        if response.status_code >= 400:
            raise AgentServiceError(502, "EMBEDDING_REQUEST_FAILED", "Embedding 生成失败")
        try:
            vectors = response.json()["embeddings"]
            if not isinstance(vectors, list):
                raise TypeError("embeddings must be a list")
            parsed = [[float(value) for value in vector] for vector in vectors]
        except (KeyError, TypeError, ValueError) as error:
            raise AgentServiceError(
                502, "EMBEDDING_RESPONSE_INVALID", "Embedding 响应无法解析"
            ) from error
        validate_embeddings(parsed, len(texts), self.dimension)
        return parsed
