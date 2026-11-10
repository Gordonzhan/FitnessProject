from __future__ import annotations

from typing import Annotated
from uuid import uuid4

from mcp.server import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.server.mcpserver.tools import Tool
from mcp.types import ToolAnnotations
from pydantic import ConfigDict, Field

from app.api.errors import AgentServiceError
from app.core.config import Settings, get_settings
from app.rag.factory import build_knowledge_retriever
from app.rag.models import KnowledgeSearchRequest, KnowledgeSearchResponse, RiskLevel
from app.rag.retriever import HybridKnowledgeRetriever
from app.tools.public_catalog import (
    ExerciseCatalogSearchResponse,
    PublicExerciseCatalogClient,
)

READ_ONLY = ToolAnnotations(readOnlyHint=True, openWorldHint=False)


def build_mcp_server(
    settings: Settings | None = None,
    *,
    knowledge: HybridKnowledgeRetriever | None = None,
    catalog: PublicExerciseCatalogClient | None = None,
) -> MCPServer:
    """创建只注册两个公共只读 Tool 的本地 MCP Server。"""

    active = settings or get_settings()
    retriever = knowledge
    if retriever is None and active.knowledge_enabled:
        retriever = build_knowledge_retriever(active)
    catalog_client = catalog or PublicExerciseCatalogClient(active)

    async def search_fitness_knowledge(
        query: Annotated[str, Field(min_length=2, max_length=300)],
        top_k: Annotated[int, Field(ge=1, le=10)] = 3,
        topics: Annotated[list[str] | None, Field(max_length=5)] = None,
        language: Annotated[str, Field(pattern=r"^[a-z]{2}(?:-[A-Z]{2})?$")] = "zh-CN",
        max_risk_level: RiskLevel = RiskLevel.MODERATE,
    ) -> KnowledgeSearchResponse:
        """检索本地已审核的通用健身知识；知识不足时明确返回拒答。"""

        if retriever is None:
            raise ToolError("[KNOWLEDGE_DISABLED] 健身知识库尚未启用")
        try:
            return await retriever.search(
                KnowledgeSearchRequest(
                    query=query,
                    top_k=top_k,
                    topics=topics or [],
                    language=language,
                    max_risk_level=max_risk_level,
                )
            )
        except AgentServiceError as error:
            raise _safe_tool_error(error) from error

    async def search_exercise_catalog(
        query: Annotated[str, Field(min_length=1, max_length=50)],
        limit: Annotated[int, Field(ge=1, le=10)] = 5,
    ) -> ExerciseCatalogSearchResponse:
        """查询 Java 权威公共动作目录，不接收或读取任何用户身份和业务数据。"""

        try:
            return await catalog_client.search(query, limit, f"mcp-{uuid4().hex}")
        except AgentServiceError as error:
            raise _safe_tool_error(error) from error

    tools = [
        _strict_tool(
            Tool.from_function(
                search_fitness_knowledge,
                title="检索已审核健身知识",
                annotations=READ_ONLY,
                structured_output=True,
            )
        ),
        _strict_tool(
            Tool.from_function(
                search_exercise_catalog,
                title="检索公共训练动作目录",
                annotations=READ_ONLY,
                structured_output=True,
            )
        ),
    ]
    return MCPServer(
        name="fitness-read-only-demo",
        title="Fitness Read-only MCP Demo",
        description="只查询已审核健身知识和公共动作目录，不读取用户数据，不提供写操作。",
        instructions=(
            "只能调用已注册的两个只读工具。不得将结果解释为医疗诊断、治疗或康复处方。"
        ),
        version="0.1.0",
        tools=tools,
    )


def _safe_tool_error(error: AgentServiceError) -> ToolError:
    return ToolError(f"[{error.code}] {error.message}")


def _strict_tool(tool: Tool) -> Tool:
    """SDK 默认忽略额外参数；Demo 必须显式拒绝 userId 等未声明字段。"""

    model = tool.fn_metadata.arg_model
    model.model_config = ConfigDict(arbitrary_types_allowed=True, extra="forbid")
    model.model_rebuild(force=True)
    tool.parameters = model.model_json_schema(by_alias=True)
    return tool


mcp = build_mcp_server()


def main() -> None:
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
