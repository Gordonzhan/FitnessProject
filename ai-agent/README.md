# Fitness AI Agent

独立 Python 服务，提供本地多模态食物识别、可编辑训练草稿、健身对话和带来源的知识检索。
FastAPI 提供接口，Pydantic 约束数据，LangGraph 组织训练生成工作流，Ollama 运行本地模型。
会话与业务记录由 Java 持久化，Python 不连接业务 MySQL。

## 安装与启动

需要 Python 3.12–3.14，从仓库根目录执行：

```bash
cd ai-agent
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e '.[dev]'
cp .env.example .env
make run
```

已有 `.env` 时保留原文件，按模板合并新增变量。健康检查为 `http://127.0.0.1:8000/health`，
交互接口文档为 `http://127.0.0.1:8000/docs`。默认 Fake Provider 用于可重复开发测试。

## 本地模型

```bash
ollama pull qwen3-vl:8b-instruct
ollama pull qwen3:8b
```

在 `ai-agent/.env` 中启用：

```properties
FITNESS_AGENT_PROVIDER=ollama
FITNESS_AGENT_RECIPE_PROVIDER=ollama
FITNESS_AGENT_WORKOUT_PROVIDER=ollama
FITNESS_AGENT_CHAT_PROVIDER=ollama
FITNESS_AGENT_OLLAMA_BASE_URL=http://127.0.0.1:11434
FITNESS_AGENT_OLLAMA_MODEL=qwen3-vl:8b-instruct
FITNESS_AGENT_OLLAMA_RECIPE_MODEL=qwen3:8b
FITNESS_AGENT_OLLAMA_WORKOUT_MODEL=qwen3:8b
FITNESS_AGENT_OLLAMA_AGENT_MODEL=qwen3:8b
FITNESS_AGENT_JAVA_INTERNAL_BASE_URL=http://127.0.0.1:8080/api
```

模型名称可配置，运行前确认本机资源足以加载。修改 `.env` 后重启服务。
图片识别最多接受 3 张图，默认每张不超过 10 MB；图片地址需要直接返回图片内容。
`FITNESS_AGENT_ALLOW_PRIVATE_IMAGE_URLS` 默认关闭，仅在受控本地图片测试时开启。

## Java 联调与确认流程

在 `backend/.env` 中设置 `FITNESS_AGENT_ENABLED=true` 和本地 Agent 地址。
Java 的 `FITNESS_AGENT_SERVICE_TOKEN` 与 Python 的同名变量、`FITNESS_AGENT_JAVA_SERVICE_TOKEN`
必须一致；Java 还需配置独立的 `FITNESS_AGENT_CONTEXT_SECRET`。
完整变量见 [.env.example](.env.example) 和[后端配置](../backend/.env.example)。

Java 签发短时效用户上下文，Python 只在回调 Java 只读工具时原样传递，不将其放入模型上下文。
工具参数由严格 Schema 校验，工具调用轮次和超时均有上限。

- 食物识别返回食材与份量草稿；菜名和步骤属于模型推测，最终营养由 Java 重算。
- 训练工作流只接受 Java 提供的候选动作，经过结构与业务校验后返回草稿；失败时使用受规则约束的降级路径。
- 对话可读取个人记录、检索知识并返回训练草稿卡，持久化操作仍需要小程序中的明确确认。

## 知识检索

在已激活的虚拟环境中启动本地 Qdrant，并准备 Embedding 模型：

```bash
docker compose -f docker-compose.rag.yml up -d
ollama pull nomic-embed-text
```

在 `.env` 中设置：

```properties
FITNESS_AGENT_KNOWLEDGE_ENABLED=true
FITNESS_AGENT_EMBEDDING_PROVIDER=ollama
FITNESS_AGENT_EMBEDDING_MODEL=nomic-embed-text
FITNESS_AGENT_EMBEDDING_DIMENSION=768
FITNESS_AGENT_QDRANT_URL=http://127.0.0.1:6333
FITNESS_AGENT_QDRANT_COLLECTION=fitness_knowledge_local_v1
```

```bash
make rag-index
make rag-search QUERY="力量训练组间应该休息多久"
make rag-eval
```

仅 `APPROVED` 通用知识进入索引；用户记录不会写入向量库。检索合并关键词与向量结果，
按主题、语言和风险过滤，返回来源与版本。低相关或知识不足时返回无足够依据。
具体动作编号、器械与动作步骤仍以 Java 公共动作目录为准。

语料升级或删除后，`make rag-rebuild-local` 可以删除并重建本地 Collection；
该命令只允许名称以 `fitness_knowledge_local` 开头的集合，执行前确认目标。
语料结构和来源见 [knowledge/README.md](knowledge/README.md)。

离线评测使用固定问题、Fake Embedding 和内存向量库，检查 Recall@K、MRR、引用、拒答和排序约束。
样本规模较小，结果用于确定性回归，不能代表真实模型的完整质量。

## 本地只读 MCP Demo

通过 stdio 暴露两个工具：`search_fitness_knowledge` 与 `search_exercise_catalog`。
不注册用户数据工具或写入工具，不监听公网端口。

```bash
make mcp-test
# 真实调用需要 Java、Qdrant、Ollama 已启动，并完成知识入库
make mcp-demo
# 可选交互调试，首次运行需要下载 npm 包
make mcp-inspector
```

外部 MCP 客户端可配置 `python -m app.mcp_demo.server`，将 Python 路径指向本项目虚拟环境，
工作目录指向 `ai-agent`。公共动作查询需要配置 Java 服务令牌；服务不可用时返回稳定错误码。

## 质量检查与日志

```bash
make check
make rag-eval
```

`make check` 运行 Ruff、Mypy 和 pytest。普通测试使用 Fake Provider、Mock Transport 与内存存储，
不需要真实模型、MySQL 或云服务。

结构化日志默认写入被 Git 忽略的 `logs/fitness-agent.log`，记录请求标识、错误码和阶段耗时，
不记录完整用户提示词、模型响应或凭证。可用 `FITNESS_AGENT_LOG_FILE` 覆盖路径。
当前服务在本地运行，最新功能尚未部署腾讯云，云端到本地的通信链路仍待完成。
