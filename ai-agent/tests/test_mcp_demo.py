from __future__ import annotations

from pathlib import Path

import httpx
import pytest
from mcp import Client

from app.core.config import Settings
from app.mcp_demo.server import build_mcp_server
from app.rag.factory import build_knowledge_retriever
from app.tools.public_catalog import PublicExerciseCatalogClient

CORPUS = Path(__file__).resolve().parents[1] / "knowledge" / "documents"


def settings() -> Settings:
    return Settings(
        environment="test",
        provider="fake",
        service_token="",
        java_service_token="mcp-demo-service-token",
        java_internal_base_url="http://java.test/api",
        knowledge_enabled=True,
        knowledge_directory=str(CORPUS),
        embedding_provider="fake",
        embedding_dimension=128,
    )


@pytest.mark.asyncio
async def test_mcp_lists_only_two_public_read_only_tools() -> None:
    active = settings()
    retriever = build_knowledge_retriever(active, memory=True)
    await retriever.index(rebuild=True)
    server = build_mcp_server(active, knowledge=retriever)

    async with Client(server, raise_exceptions=True) as client:
        result = await client.list_tools()

    tools = {item.name: item for item in result.tools}
    assert set(tools) == {"search_fitness_knowledge", "search_exercise_catalog"}
    assert all(item.annotations and item.annotations.read_only_hint for item in tools.values())
    assert all(item.annotations and not item.annotations.open_world_hint for item in tools.values())
    assert all(item.input_schema.get("additionalProperties") is False for item in tools.values())
    assert "userId" not in str(tools["search_fitness_knowledge"].input_schema)
    assert "userId" not in str(tools["search_exercise_catalog"].input_schema)


@pytest.mark.asyncio
async def test_mcp_knowledge_search_returns_approved_citations_and_rejects_user_id() -> None:
    active = settings()
    retriever = build_knowledge_retriever(active, memory=True)
    await retriever.index(rebuild=True)
    server = build_mcp_server(active, knowledge=retriever)

    async with Client(server, raise_exceptions=True) as client:
        result = await client.call_tool(
            "search_fitness_knowledge",
            {"query": "力量训练组间休息多久", "top_k": 3},
        )
        invalid = await client.call_tool(
            "search_fitness_knowledge",
            {"query": "力量训练组间休息多久", "userId": 7},
        )

    assert result.is_error is False
    assert result.structured_content is not None
    assert result.structured_content["sufficient"] is True
    assert result.structured_content["citations"]
    assert invalid.is_error is True


@pytest.mark.asyncio
async def test_mcp_exercise_search_uses_service_identity_without_user_context() -> None:
    captured: dict[str, object] = {}

    def java_response(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["authorization"] = request.headers.get("Authorization")
        captured["userContext"] = request.headers.get("X-Agent-User-Context")
        captured["body"] = request.read().decode()
        return httpx.Response(
            200,
            json={
                "code": 200,
                "message": "success",
                "data": {
                    "items": [
                        {
                            "exerciseCode": "BARBELL_BACK_SQUAT",
                            "name": "深蹲",
                            "category": "力量训练",
                            "primaryMuscles": "股四头肌,臀大肌",
                            "equipment": "杠铃",
                            "intensity": "高",
                            "instructions": "稳定下蹲后站起",
                            "safetyCues": "保持脊柱中立",
                            "commonMistakes": "膝盖内扣",
                            "limitationTags": ["KNEE"],
                            "alternativeExerciseCodes": ["GOBLET_SQUAT"],
                            "defaultSets": 4,
                            "defaultReps": 10,
                            "defaultRestSeconds": 90,
                            "defaultRpe": 7,
                            "tempo": "3-1-1",
                        }
                    ]
                },
            },
        )

    active = settings()
    catalog = PublicExerciseCatalogClient(active, httpx.MockTransport(java_response))
    server = build_mcp_server(active, catalog=catalog)

    async with Client(server, raise_exceptions=True) as client:
        result = await client.call_tool(
            "search_exercise_catalog", {"query": "深蹲", "limit": 3}
        )
        write_attempt = await client.call_tool("save_workout", {})

    assert result.is_error is False
    assert result.structured_content is not None
    assert result.structured_content["items"][0]["exerciseCode"] == "BARBELL_BACK_SQUAT"
    assert captured == {
        "url": "http://java.test/api/internal/ai/public/tools/exercises/search",
        "authorization": "Bearer mcp-demo-service-token",
        "userContext": None,
        "body": '{"keyword":"深蹲","limit":3}',
    }
    assert write_attempt.is_error is True


@pytest.mark.asyncio
async def test_mcp_catalog_maps_upstream_failures_to_stable_safe_error() -> None:
    def timeout(_: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("sensitive upstream detail")

    active = settings()
    catalog = PublicExerciseCatalogClient(active, httpx.MockTransport(timeout))
    server = build_mcp_server(active, catalog=catalog)

    async with Client(server, raise_exceptions=True) as client:
        result = await client.call_tool(
            "search_exercise_catalog", {"query": "深蹲", "limit": 3}
        )

    assert result.is_error is True
    text = " ".join(getattr(item, "text", "") for item in result.content)
    assert "MCP_CATALOG_TIMEOUT" in text
    assert "sensitive upstream detail" not in text
