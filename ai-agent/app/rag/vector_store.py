from __future__ import annotations

from typing import Protocol

import httpx

from app.api.errors import AgentServiceError
from app.rag.models import KnowledgeChunk, RiskLevel


class VectorMatch:
    def __init__(self, chunk: KnowledgeChunk, score: float) -> None:
        self.chunk = chunk
        self.score = score


class VectorStore(Protocol):
    async def ensure_collection(self, dimension: int, rebuild: bool = False) -> None: ...

    async def replace_points(
        self, chunks: list[KnowledgeChunk], vectors: list[list[float]]
    ) -> None: ...

    async def search(
        self,
        vector: list[float],
        limit: int,
        topics: list[str],
        versions: list[str],
        language: str,
        max_risk_level: RiskLevel,
    ) -> list[VectorMatch]: ...


class InMemoryVectorStore:
    def __init__(self) -> None:
        self.dimension = 0
        self._points: list[tuple[KnowledgeChunk, list[float]]] = []

    async def ensure_collection(self, dimension: int, rebuild: bool = False) -> None:
        if self.dimension and self.dimension != dimension and not rebuild:
            raise AgentServiceError(409, "QDRANT_DIMENSION_MISMATCH", "向量维度与现有索引不一致")
        if rebuild:
            self._points.clear()
        self.dimension = dimension

    async def replace_points(
        self, chunks: list[KnowledgeChunk], vectors: list[list[float]]
    ) -> None:
        self._points = list(zip(chunks, vectors, strict=True))

    async def search(
        self,
        vector: list[float],
        limit: int,
        topics: list[str],
        versions: list[str],
        language: str,
        max_risk_level: RiskLevel,
    ) -> list[VectorMatch]:
        allowed = _allowed_risks(max_risk_level)
        matches = []
        for chunk, candidate in self._points:
            if chunk.language != language or chunk.risk_level.value not in allowed:
                continue
            if topics and not set(topics).intersection(chunk.topics):
                continue
            if versions and chunk.version not in versions:
                continue
            score = sum(left * right for left, right in zip(vector, candidate, strict=True))
            matches.append(VectorMatch(chunk, max(0.0, min(1.0, (score + 1) / 2))))
        return sorted(matches, key=lambda item: (-item.score, item.chunk.chunk_id))[:limit]


class QdrantVectorStore:
    def __init__(
        self,
        url: str,
        collection: str,
        api_key: str,
        timeout: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._url = url.rstrip("/")
        self._collection = collection
        self._api_key = api_key
        self._timeout = timeout
        self._transport = transport

    async def ensure_collection(self, dimension: int, rebuild: bool = False) -> None:
        path = f"/collections/{self._collection}"
        response = await self._request("GET", path, allow_404=True)
        if response.status_code == 200:
            try:
                existing = int(response.json()["result"]["config"]["params"]["vectors"]["size"])
            except (KeyError, TypeError, ValueError) as error:
                raise AgentServiceError(
                    502, "QDRANT_RESPONSE_INVALID", "Qdrant Collection 信息无法解析"
                ) from error
            if existing == dimension and not rebuild:
                return
            if not rebuild:
                raise AgentServiceError(
                    409, "QDRANT_DIMENSION_MISMATCH", "向量维度与现有索引不一致"
                )
            if not self._collection.startswith("fitness_knowledge_local"):
                raise AgentServiceError(
                    403,
                    "QDRANT_REBUILD_FORBIDDEN",
                    "只允许重建明确命名的本地知识 Collection",
                )
            await self._request("DELETE", path)
        await self._request(
            "PUT", path, json={"vectors": {"size": dimension, "distance": "Cosine"}}
        )

    async def replace_points(
        self, chunks: list[KnowledgeChunk], vectors: list[list[float]]
    ) -> None:
        points = [
            {"id": chunk.point_id, "vector": vector, "payload": chunk.payload()}
            for chunk, vector in zip(chunks, vectors, strict=True)
        ]
        await self._request(
            "PUT",
            f"/collections/{self._collection}/points?wait=true",
            json={"points": points},
        )

    async def search(
        self,
        vector: list[float],
        limit: int,
        topics: list[str],
        versions: list[str],
        language: str,
        max_risk_level: RiskLevel,
    ) -> list[VectorMatch]:
        must: list[dict[str, object]] = [
            {"key": "reviewStatus", "match": {"value": "APPROVED"}},
            {"key": "language", "match": {"value": language}},
            {"key": "riskLevel", "match": {"any": _allowed_risks(max_risk_level)}},
        ]
        if topics:
            must.append({"key": "topics", "match": {"any": topics}})
        if versions:
            must.append({"key": "version", "match": {"any": versions}})
        response = await self._request(
            "POST",
            f"/collections/{self._collection}/points/search",
            json={"vector": vector, "limit": limit, "with_payload": True, "filter": {"must": must}},
        )
        try:
            values = response.json()["result"]
            return [
                VectorMatch(
                    KnowledgeChunk.model_validate(
                        {**item["payload"], "pointId": item["id"]}
                    ),
                    float(item["score"]),
                )
                for item in values
            ]
        except (KeyError, TypeError, ValueError) as error:
            raise AgentServiceError(
                502, "QDRANT_RESPONSE_INVALID", "Qdrant 检索响应无法解析"
            ) from error

    async def _request(
        self,
        method: str,
        path: str,
        json: dict[str, object] | None = None,
        allow_404: bool = False,
    ) -> httpx.Response:
        headers = {"api-key": self._api_key} if self._api_key else {}
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                response = await client.request(
                    method, self._url + path, json=json, headers=headers, timeout=self._timeout
                )
        except httpx.TimeoutException as error:
            raise AgentServiceError(504, "QDRANT_TIMEOUT", "知识索引访问超时") from error
        except httpx.RequestError as error:
            raise AgentServiceError(503, "QDRANT_UNAVAILABLE", "知识索引暂时不可用") from error
        if allow_404 and response.status_code == 404:
            return response
        if response.status_code >= 400:
            raise AgentServiceError(502, "QDRANT_REQUEST_FAILED", "知识索引请求失败")
        return response


def _allowed_risks(maximum: RiskLevel) -> list[str]:
    order = [RiskLevel.LOW, RiskLevel.MODERATE, RiskLevel.HIGH]
    return [item.value for item in order[: order.index(maximum) + 1]]
