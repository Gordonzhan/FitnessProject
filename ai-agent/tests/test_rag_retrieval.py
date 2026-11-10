from __future__ import annotations

from pathlib import Path

import pytest

from app.core.config import Settings
from app.rag.evaluation import evaluate
from app.rag.factory import build_knowledge_retriever
from app.rag.models import KnowledgeSearchRequest
from app.rag.retriever import infer_query_topics

ROOT = Path(__file__).resolve().parents[1]


def settings() -> Settings:
    return Settings(
        environment="local",
        knowledge_directory=str(ROOT / "knowledge" / "documents"),
        embedding_provider="fake",
        embedding_dimension=128,
        knowledge_minimum_score=0.35,
    )


@pytest.mark.asyncio
async def test_hybrid_search_returns_only_approved_grounded_results_and_deduped_citations() -> None:
    retriever = build_knowledge_retriever(settings(), memory=True)
    await retriever.index(rebuild=True)

    response = await retriever.search(
        KnowledgeSearchRequest(query="力量训练组间休息多久", top_k=5)
    )

    assert response.sufficient
    assert response.results
    assert response.results[0].document_id == "KB_ACSM_PROGRESSION_2009"
    assert {
        "KB_AHA_WARMUP_COOLDOWN_2024",
        "KB_RESISTANCE_WARMUP_REVIEW_2021",
    }.isdisjoint(item.document_id for item in response.results[:3])
    assert all("CANDIDATE" not in item.document_id for item in response.results)
    assert len({(item.document_id, item.version) for item in response.citations}) == len(
        response.citations
    )


@pytest.mark.parametrize(
    "query",
    [
        "组间休息多久",
        "组间歇怎么安排",
        "组间恢复要几分钟",
        "每组之间休息多长时间",
    ],
)
def test_rest_interval_queries_map_to_stable_topic(query: str) -> None:
    assert infer_query_topics(query) == ["rest_intervals"]


@pytest.mark.asyncio
async def test_medical_request_returns_explicit_insufficient_knowledge() -> None:
    retriever = build_knowledge_retriever(settings(), memory=True)
    await retriever.index(rebuild=True)

    response = await retriever.search(
        KnowledgeSearchRequest(query="半月板撕裂后给我康复处方")
    )

    assert not response.sufficient
    assert response.message == "没有足够可靠的知识依据"
    assert response.results == []


@pytest.mark.asyncio
async def test_version_filter_is_applied_to_keyword_and_vector_results() -> None:
    retriever = build_knowledge_retriever(settings(), memory=True)
    await retriever.index(rebuild=True)

    response = await retriever.search(
        KnowledgeSearchRequest(
            query="力量训练频率",
            versions=["2018-2ed-project-summary-v1"],
        )
    )

    assert response.results
    assert {item.version for item in response.results} == {"2018-2ed-project-summary-v1"}


@pytest.mark.asyncio
async def test_exact_exercise_name_in_title_is_ranked_before_generic_error_chunks() -> None:
    retriever = build_knowledge_retriever(settings(), memory=True)
    await retriever.index(rebuild=True)

    response = await retriever.search(
        KnowledgeSearchRequest(query="深蹲怎么做，有哪些常见错误？", top_k=3)
    )

    assert response.results
    assert response.results[0].document_id == "KB_MAYO_SQUAT_2025"


@pytest.mark.asyncio
async def test_retrieval_eval_meets_v1_baseline() -> None:
    retriever = build_knowledge_retriever(settings(), memory=True)
    await retriever.index(rebuild=True)

    result = await evaluate(retriever, ROOT / "evals" / "retrieval_v1.json")

    assert result.passed_baseline
    assert result.sample_count == 17
    assert result.citation_accuracy == 1.0
    assert result.refusal_accuracy == 1.0
    assert result.ranking_constraint_accuracy == 1.0
