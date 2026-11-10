from __future__ import annotations

import json
from pathlib import Path

from pydantic import Field

from app.rag.models import KnowledgeSearchRequest
from app.rag.retriever import HybridKnowledgeRetriever
from app.schemas.base import ApiModel


class RetrievalEvalCase(ApiModel):
    case_id: str
    query: str
    expected_document_ids: list[str] = Field(default_factory=list)
    expected_chunk_ids: list[str] = Field(default_factory=list)
    topic: str
    should_refuse: bool = False
    expected_top1_document_id: str | None = None
    forbidden_document_ids_in_top_k: list[str] = Field(default_factory=list)
    forbidden_top_k: int = Field(default=3, ge=1, le=10)


class RetrievalEvalResult(ApiModel):
    sample_count: int
    recall_at_k: float
    mrr: float
    citation_accuracy: float
    refusal_accuracy: float
    ranking_constraint_accuracy: float
    passed_baseline: bool


def load_eval_cases(dataset: Path) -> list[RetrievalEvalCase]:
    return [
        RetrievalEvalCase.model_validate(item)
        for item in json.loads(dataset.read_text(encoding="utf-8"))
    ]


async def evaluate(
    retriever: HybridKnowledgeRetriever, dataset: Path, top_k: int = 5
) -> RetrievalEvalResult:
    cases = load_eval_cases(dataset)
    recall_total = 0.0
    reciprocal_total = 0.0
    citation_correct = 0
    citation_total = 0
    refusal_correct = 0
    refusal_total = 0
    ranking_correct = 0
    ranking_total = 0
    for case in cases:
        response = await retriever.search(KnowledgeSearchRequest(query=case.query, top_k=top_k))
        returned_documents = [item.document_id for item in response.results]
        returned_chunks = [item.chunk_id for item in response.results]
        expected = set(case.expected_document_ids)
        expected_chunks = set(case.expected_chunk_ids)
        if expected or expected_chunks:
            hits = len(expected.intersection(returned_documents)) + len(
                expected_chunks.intersection(returned_chunks)
            )
            recall_total += min(1.0, hits / max(1, len(expected) + len(expected_chunks)))
            ranks = [
                index + 1
                for index, item in enumerate(response.results)
                if item.document_id in expected or item.chunk_id in expected_chunks
            ]
            reciprocal_total += 1 / min(ranks) if ranks else 0
        for citation in response.citations:
            citation_total += 1
            if any(
                item.document_id == citation.document_id and item.version == citation.version
                for item in response.results
            ):
                citation_correct += 1
        if case.should_refuse:
            refusal_total += 1
            refusal_correct += int(not response.sufficient)
        if case.expected_top1_document_id or case.forbidden_document_ids_in_top_k:
            ranking_total += 1
            top1_correct = (
                case.expected_top1_document_id is None
                or bool(returned_documents)
                and returned_documents[0] == case.expected_top1_document_id
            )
            forbidden = set(case.forbidden_document_ids_in_top_k)
            forbidden_absent = not forbidden.intersection(
                returned_documents[: case.forbidden_top_k]
            )
            ranking_correct += int(top1_correct and forbidden_absent)
    eligible = sum(bool(case.expected_document_ids or case.expected_chunk_ids) for case in cases)
    recall = recall_total / max(1, eligible)
    mrr = reciprocal_total / max(1, eligible)
    citation_accuracy = citation_correct / max(1, citation_total)
    refusal_accuracy = refusal_correct / max(1, refusal_total)
    ranking_constraint_accuracy = ranking_correct / max(1, ranking_total)
    return RetrievalEvalResult(
        sample_count=len(cases),
        recall_at_k=round(recall, 4),
        mrr=round(mrr, 4),
        citation_accuracy=round(citation_accuracy, 4),
        refusal_accuracy=round(refusal_accuracy, 4),
        ranking_constraint_accuracy=round(ranking_constraint_accuracy, 4),
        passed_baseline=(
            recall >= 0.8
            and mrr >= 0.65
            and citation_accuracy >= 1.0
            and refusal_accuracy >= 1.0
            and ranking_constraint_accuracy >= 1.0
        ),
    )
