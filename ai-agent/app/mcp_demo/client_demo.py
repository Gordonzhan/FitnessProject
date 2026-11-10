from __future__ import annotations

import asyncio
import json
import sys
from typing import cast

from mcp import Client
from mcp.client.stdio import StdioServerParameters
from mcp.types import CallToolResult


def _structured(result: CallToolResult, tool_name: str) -> dict[str, object]:
    if result.is_error or result.structured_content is None:
        messages = [getattr(item, "text", "") for item in result.content]
        detail = " ".join(message for message in messages if message)
        raise RuntimeError(f"{tool_name} 调用失败: {detail or '没有结构化结果'}")
    return cast(dict[str, object], result.structured_content)


async def run_demo() -> None:
    parameters = StdioServerParameters(
        command=sys.executable,
        args=["-m", "app.mcp_demo.server"],
    )
    async with Client(parameters) as client:
        tools = await client.list_tools()
        knowledge = await client.call_tool(
            "search_fitness_knowledge",
            {"query": "力量训练组间应该休息多久", "top_k": 3},
        )
        exercise = await client.call_tool(
            "search_exercise_catalog",
            {"query": "深蹲", "limit": 3},
        )
    print(json.dumps({"tools": [item.name for item in tools.tools]}, ensure_ascii=False))
    print(
        json.dumps(
            _structured(knowledge, "search_fitness_knowledge"),
            ensure_ascii=False,
            indent=2,
        )
    )
    print(
        json.dumps(
            _structured(exercise, "search_exercise_catalog"),
            ensure_ascii=False,
            indent=2,
        )
    )


def main() -> None:
    try:
        asyncio.run(run_demo())
    except RuntimeError as error:
        print(f"MCP Demo 失败：{error}", file=sys.stderr)
        raise SystemExit(1) from error


if __name__ == "__main__":
    main()
