from __future__ import annotations

from datetime import date
from pathlib import Path

from app.rag.corpus import chunk_document, load_documents
from app.rag.models import KnowledgeDocument, KnowledgeSection, ReviewStatus, RiskLevel

CORPUS = Path(__file__).resolve().parents[1] / "knowledge" / "documents"


def test_corpus_has_complete_metadata_and_only_approved_documents_are_indexable() -> None:
    documents = load_documents(CORPUS)

    assert len(documents) == 14
    assert sum(item.review_status == ReviewStatus.APPROVED for item in documents) == 13
    assert all(
        item.reviewed_at
        for item in documents
        if item.review_status == ReviewStatus.APPROVED
    )
    assert any(item.review_status == ReviewStatus.CANDIDATE for item in documents)
    approved_sections = sum(
        len(item.sections)
        for item in documents
        if item.review_status == ReviewStatus.APPROVED
    )
    assert approved_sections == 40


def test_chunk_ids_are_stable_for_same_document_version() -> None:
    document = load_documents(CORPUS)[0]

    first = chunk_document(document, 500)
    second = chunk_document(document, 500)

    assert [item.chunk_id for item in first] == [item.chunk_id for item in second]
    assert len({item.point_id for item in first}) == len(first)
    assert all(item.document_id == document.document_id for item in first)


def test_single_long_paragraph_is_split_to_configured_size() -> None:
    document = KnowledgeDocument(
        document_id="KB_LONG_TEST",
        title="长段落测试",
        source_name="Test Source",
        source_url="https://example.com/source",
        published_or_updated_at=date(2026, 10, 13),
        version="v1",
        topics=["strength_basics"],
        language="zh-CN",
        risk_level=RiskLevel.LOW,
        review_status=ReviewStatus.APPROVED,
        reviewed_at=date(2026, 10, 13),
        reviewer="test",
        sections=[KnowledgeSection(heading="原则", content="循序渐进" * 200)],
    )

    chunks = chunk_document(document, 200)

    assert len(chunks) > 1
    assert all(len(item.content) <= 200 for item in chunks)
