from __future__ import annotations

import hashlib
import json
import re
import uuid
from pathlib import Path

from app.rag.models import KnowledgeChunk, KnowledgeDocument, ReviewStatus

_SPACE = re.compile(r"[ \t]+")


def normalize_text(value: str) -> str:
    lines = [_SPACE.sub(" ", line).strip() for line in value.replace("\r\n", "\n").split("\n")]
    return "\n".join(line for line in lines if line)


def load_documents(directory: Path) -> list[KnowledgeDocument]:
    documents: list[KnowledgeDocument] = []
    seen: set[str] = set()
    for path in sorted(directory.glob("*.json")):
        document = KnowledgeDocument.model_validate_json(path.read_text(encoding="utf-8"))
        key = f"{document.document_id}:{document.version}"
        if key in seen:
            raise ValueError(f"duplicate knowledge document version: {key}")
        seen.add(key)
        documents.append(document)
    return documents


def chunk_document(document: KnowledgeDocument, max_chars: int = 900) -> list[KnowledgeChunk]:
    if max_chars < 200:
        raise ValueError("max_chars must be at least 200")
    chunks: list[KnowledgeChunk] = []
    for section in document.sections:
        content = normalize_text(f"{section.heading}\n{section.content}")
        paragraphs = [
            paragraph[offset : offset + max_chars]
            for paragraph in content.split("\n")
            for offset in range(0, len(paragraph), max_chars)
        ]
        groups: list[str] = []
        current = ""
        for paragraph in paragraphs:
            if current and len(current) + len(paragraph) + 1 > max_chars:
                groups.append(current)
                current = paragraph
            else:
                current = f"{current}\n{paragraph}" if current else paragraph
        if current:
            groups.append(current)
        for body in groups:
            digest = hashlib.sha256(
                f"{document.document_id}\0{document.version}\0{body}".encode()
            ).hexdigest()[:20]
            chunk_id = f"{document.document_id}:{document.version}:{digest}"
            chunks.append(
                KnowledgeChunk(
                    chunk_id=chunk_id,
                    point_id=str(uuid.uuid5(uuid.NAMESPACE_URL, chunk_id)),
                    document_id=document.document_id,
                    title=document.title,
                    content=body,
                    source_url=str(document.source_url),
                    source_name=document.source_name,
                    version=document.version,
                    topics=document.topics,
                    language=document.language,
                    risk_level=document.risk_level,
                    review_status=document.review_status,
                )
            )
    return chunks


def load_approved_chunks(directory: Path, max_chars: int = 900) -> list[KnowledgeChunk]:
    return [
        chunk
        for document in load_documents(directory)
        if document.review_status == ReviewStatus.APPROVED
        for chunk in chunk_document(document, max_chars)
    ]


def manifest(directory: Path) -> str:
    rows = [
        {
            "documentId": item.document_id,
            "version": item.version,
            "reviewStatus": item.review_status,
            "reviewedAt": item.reviewed_at,
        }
        for item in load_documents(directory)
    ]
    return json.dumps(rows, ensure_ascii=False, indent=2, default=str)
