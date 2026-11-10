MCP_INSPECTOR_VERSION ?= 2.11.0

.PHONY: install run test lint typecheck check rag-index rag-rebuild-local rag-search rag-eval mcp-test mcp-demo mcp-inspector

install:
	python -m pip install -e '.[dev]'

run:
	uvicorn app.main:app --reload --host 127.0.0.1 --port 8000

test:
	pytest

lint:
	ruff check .

typecheck:
	mypy app tests

check: lint typecheck test

rag-index:
	python -m app.rag.cli index

rag-rebuild-local:
	python -m app.rag.cli rebuild-local

rag-search:
	@test -n "$(QUERY)" || (echo '用法: make rag-search QUERY="力量训练频率"' && exit 2)
	python -m app.rag.cli search "$(QUERY)"

rag-eval:
	python -m app.rag.cli eval

mcp-test:
	pytest tests/test_mcp_demo.py tests/test_fitness_tools.py

mcp-demo:
	python -m app.mcp_demo.client_demo

mcp-inspector:
	npx --yes --package=@modelcontextprotocol/inspector@$(MCP_INSPECTOR_VERSION) -- mcp-inspector --protocol-era auto --cwd "$(CURDIR)" -- "$(CURDIR)/.venv/bin/python" -m app.mcp_demo.server
