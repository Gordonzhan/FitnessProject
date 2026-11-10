from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path

from app.core.config import Settings
from app.rag.evaluation import evaluate
from app.rag.factory import build_knowledge_retriever
from app.rag.models import KnowledgeSearchRequest

EVAL_DATASET = Path(__file__).resolve().parents[2] / "evals" / "retrieval_v1.json"


async def _run() -> None:
    parser = argparse.ArgumentParser(description="FitnessProject reviewed knowledge operations")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("index", help="idempotently create/check and upsert approved chunks")
    subparsers.add_parser(
        "rebuild-local", help="delete and rebuild only the configured local collection"
    )
    search = subparsers.add_parser("search", help="search the configured local Qdrant collection")
    search.add_argument("query")
    search.add_argument("--top-k", type=int, default=5)
    search.add_argument("--topic", action="append", default=[])
    search.add_argument("--version", action="append", default=[])
    search.add_argument("--language", default="zh-CN")
    evaluation = subparsers.add_parser("eval", help="run deterministic offline retrieval eval")
    evaluation.add_argument("--top-k", type=int, default=5)
    args = parser.parse_args()
    settings = Settings()
    if args.command == "eval":
        settings = settings.model_copy(update={
            "embedding_provider": "fake",
            "embedding_dimension": 128,
            "knowledge_minimum_score": 0.35,
        })
        retriever = build_knowledge_retriever(settings, memory=True)
        await retriever.index(rebuild=True, batch_size=settings.embedding_batch_size)
        eval_result = await evaluate(retriever, EVAL_DATASET, args.top_k)
        print(eval_result.model_dump_json(by_alias=True, indent=2))
        if not eval_result.passed_baseline:
            raise SystemExit(1)
        return
    retriever = build_knowledge_retriever(settings)
    if args.command in {"index", "rebuild-local"}:
        count = await retriever.index(
            rebuild=args.command == "rebuild-local",
            batch_size=settings.embedding_batch_size,
        )
        print(json.dumps({"collection": settings.qdrant_collection, "indexedChunks": count}))
        return
    search_result = await retriever.search(
        KnowledgeSearchRequest(
            query=args.query,
            top_k=args.top_k,
            topics=args.topic,
            versions=args.version,
            language=args.language,
        )
    )
    print(search_result.model_dump_json(by_alias=True, indent=2))


if __name__ == "__main__":
    asyncio.run(_run())
