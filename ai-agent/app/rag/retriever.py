from __future__ import annotations

import re
import time

from app.core.logging import logger
from app.rag.embeddings import EmbeddingProvider
from app.rag.models import (
    KnowledgeChunk,
    KnowledgeCitation,
    KnowledgeSearchHit,
    KnowledgeSearchRequest,
    KnowledgeSearchResponse,
    ReviewStatus,
)
from app.rag.vector_store import VectorStore

_ASCII = re.compile(r"[a-z0-9]+")
_MEDICAL = re.compile(r"诊断|治疗|康复处方|骨折|撕裂|胸痛|呼吸困难|昏厥|急性疼痛|孕期")
_REST_INTERVAL_QUERY = re.compile(
    r"组间(?:休息|歇|间歇|恢复)"
    r"|组与组之间(?:休息|恢复)"
    r"|每组之间(?:休息|恢复)"
    r"|休息(?:多久|多长|几分钟|时间)"
)
_REST_INTERVAL_EXACT_PHRASES = ("组间休息", "3至5分钟", "1至2分钟", "少于90秒")
_REST_INTERVAL_CONFLICT_TOPICS = {
    "warmup_recovery",
    "resistance_warmup",
    "aerobic_warmup",
    "cooldown",
}
_TITLE_BOOST_PHRASES = (
    "深蹲", "弓步", "硬拉", "髋铰链", "俯卧撑", "坐姿划船", "划船", "高位下拉",
    "引体向上",
)


def tokens(value: str) -> set[str]:
    normalized = "".join(value.lower().split())
    chinese = {normalized[index : index + 2] for index in range(max(0, len(normalized) - 1))}
    return chinese | set(_ASCII.findall(value.lower()))


def infer_query_topics(query: str) -> list[str]:
    """Map precise query intents to stable corpus topics without using an LLM."""
    topics: list[str] = []
    if _REST_INTERVAL_QUERY.search("".join(query.split())):
        topics.append("rest_intervals")
    return topics


class HybridKnowledgeRetriever:
    def __init__(
        self,
        chunks: list[KnowledgeChunk],
        embeddings: EmbeddingProvider,
        store: VectorStore,
        minimum_score: float = 0.3,
    ) -> None:
        self._chunks = [chunk for chunk in chunks if chunk.review_status == ReviewStatus.APPROVED]
        self._embeddings = embeddings
        self._store = store
        self._minimum_score = minimum_score

    async def index(self, rebuild: bool = False, batch_size: int = 32) -> int:
        await self._store.ensure_collection(self._embeddings.dimension, rebuild=rebuild)
        vectors: list[list[float]] = []
        for offset in range(0, len(self._chunks), batch_size):
            vectors.extend(await self._embeddings.embed(
                [chunk.content for chunk in self._chunks[offset : offset + batch_size]]
            ))
        await self._store.replace_points(self._chunks, vectors)
        return len(self._chunks)

    async def search(self, request: KnowledgeSearchRequest) -> KnowledgeSearchResponse:
        total_started = time.perf_counter()
        if _MEDICAL.search(request.query):
            return KnowledgeSearchResponse(
                sufficient=False,
                message="没有足够可靠的知识依据",
            )
        inferred_topics = infer_query_topics(request.query)
        effective_request = request
        if inferred_topics and not request.topics:
            effective_request = request.model_copy(update={"topics": inferred_topics})
        embedding_started = time.perf_counter()
        vector = (await self._embeddings.embed([request.query]))[0]
        embedding_ms = _elapsed(embedding_started)
        keyword_started = time.perf_counter()
        keyword = self._keyword_scores(effective_request)
        keyword_ms = _elapsed(keyword_started)
        vector_started = time.perf_counter()
        semantic = await self._store.search(
            vector,
            max(request.top_k * 3, 10),
            effective_request.topics,
            effective_request.versions,
            effective_request.language,
            effective_request.max_risk_level,
        )
        vector_ms = _elapsed(vector_started)
        merge_started = time.perf_counter()
        vector_scores = {item.chunk.chunk_id: item.score for item in semantic}
        chunks = {chunk.chunk_id: chunk for chunk in self._chunks}
        ranked: list[tuple[float, KnowledgeChunk]] = []
        for chunk_id in set(keyword) | set(vector_scores):
            chunk = chunks.get(chunk_id)
            if chunk is None:
                continue
            score = 0.55 * keyword.get(chunk_id, 0.0) + 0.45 * vector_scores.get(chunk_id, 0.0)
            score = self._apply_intent_adjustment(score, inferred_topics, chunk)
            ranked.append((score, chunk))
        ranked.sort(key=lambda item: (-item[0], item[1].document_id, item[1].chunk_id))
        selected = [item for item in ranked if item[0] >= self._minimum_score][: request.top_k]
        results = [
            KnowledgeSearchHit(
                chunk_id=chunk.chunk_id,
                document_id=chunk.document_id,
                title=chunk.title,
                content=chunk.content,
                source_url=chunk.source_url,
                source_name=chunk.source_name,
                version=chunk.version,
                relevance_score=round(score, 6),
            )
            for score, chunk in selected
        ]
        citations = _citations(results)
        logger.info(
            "knowledge_search_completed",
            extra={
                "topK": request.top_k,
                "inferredTopics": inferred_topics,
                "hitCount": len(results),
                "documentIds": [item.document_id for item in results],
                "embeddingDurationMs": embedding_ms,
                "keywordDurationMs": keyword_ms,
                "vectorDurationMs": vector_ms,
                "mergeDurationMs": _elapsed(merge_started),
                "durationMs": _elapsed(total_started),
            },
        )
        if not results:
            return KnowledgeSearchResponse(
                sufficient=False,
                message="没有足够可靠的知识依据",
            )
        return KnowledgeSearchResponse(
            sufficient=True,
            message="仅可依据以下已审核知识片段回答，并展示来源引用。",
            results=results,
            citations=citations,
        )

    @staticmethod
    def _apply_intent_adjustment(
        score: float, inferred_topics: list[str], chunk: KnowledgeChunk
    ) -> float:
        if "rest_intervals" not in inferred_topics:
            return score
        normalized_content = "".join((chunk.title + " " + chunk.content).lower().split())
        if "rest_intervals" in chunk.topics and any(
            phrase in normalized_content for phrase in _REST_INTERVAL_EXACT_PHRASES
        ):
            return min(1.0, score + 0.18)
        if (
            "rest_intervals" not in chunk.topics
            and _REST_INTERVAL_CONFLICT_TOPICS.intersection(chunk.topics)
        ):
            return max(0.0, score - 0.2)
        return score

    def _keyword_scores(self, request: KnowledgeSearchRequest) -> dict[str, float]:
        query = tokens(request.query)
        normalized_query = "".join(request.query.lower().split())
        allowed = {"LOW": 0, "MODERATE": 1, "HIGH": 2}
        maximum = allowed[request.max_risk_level.value]
        raw: dict[str, float] = {}
        for chunk in self._chunks:
            if chunk.language != request.language or allowed[chunk.risk_level.value] > maximum:
                continue
            if request.topics and not set(request.topics).intersection(chunk.topics):
                continue
            if request.versions and chunk.version not in request.versions:
                continue
            candidate = tokens(chunk.title + " " + chunk.content + " " + " ".join(chunk.topics))
            overlap = len(query.intersection(candidate))
            if overlap:
                score = min(1.0, overlap / max(1, len(query)))
                # 具体动作名出现在标题和问题中时，应优先于其他片段里的泛化“常见错误”等词。
                normalized_title = "".join(chunk.title.lower().split())
                if any(
                    phrase in normalized_query and phrase in normalized_title
                    for phrase in _TITLE_BOOST_PHRASES
                ):
                    score = max(score, 0.9)
                raw[chunk.chunk_id] = score
        return raw


def _citations(results: list[KnowledgeSearchHit]) -> list[KnowledgeCitation]:
    unique: dict[tuple[str, str], KnowledgeCitation] = {}
    for item in results:
        key = (item.document_id, item.version)
        unique[key] = KnowledgeCitation(
            document_id=item.document_id,
            title=item.title,
            source_name=item.source_name,
            source_url=(
                item.source_url
                if item.source_url.startswith(("https://", "http://"))
                else None
            ),
            version=item.version,
        )
    return list(unique.values())


def _elapsed(started: float) -> float:
    return round((time.perf_counter() - started) * 1000, 2)
