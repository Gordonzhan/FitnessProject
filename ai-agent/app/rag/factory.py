from __future__ import annotations

from pathlib import Path

from app.core.config import Settings
from app.rag.corpus import load_approved_chunks
from app.rag.embeddings import EmbeddingProvider, FakeEmbeddingProvider, OllamaEmbeddingProvider
from app.rag.retriever import HybridKnowledgeRetriever
from app.rag.vector_store import InMemoryVectorStore, QdrantVectorStore


def build_knowledge_retriever(
    settings: Settings, *, memory: bool = False
) -> HybridKnowledgeRetriever:
    directory = Path(settings.knowledge_directory)
    if not directory.is_absolute():
        directory = Path(__file__).resolve().parents[2] / directory
    chunks = load_approved_chunks(directory, settings.knowledge_chunk_size)
    embeddings: EmbeddingProvider
    if settings.embedding_provider == "fake":
        embeddings = FakeEmbeddingProvider(settings.embedding_dimension)
    else:
        embeddings = OllamaEmbeddingProvider(
            settings.ollama_base_url,
            settings.embedding_model,
            settings.embedding_dimension,
            settings.embedding_timeout_seconds,
        )
    store = InMemoryVectorStore() if memory else QdrantVectorStore(
        settings.qdrant_url,
        settings.qdrant_collection,
        settings.qdrant_api_key.get_secret_value(),
        settings.qdrant_timeout_seconds,
    )
    return HybridKnowledgeRetriever(
        chunks, embeddings, store, settings.knowledge_minimum_score
    )
