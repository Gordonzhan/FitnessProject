from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from app.api.errors import register_exception_handlers
from app.api.routes import router
from app.core.config import Settings, get_settings
from app.core.logging import configure_logging, logger
from app.core.middleware import RequestContextMiddleware
from app.providers.agent import build_fitness_agent_provider
from app.providers.factory import (
    build_food_recognition_provider,
    build_recipe_generation_provider,
    build_workout_generation_provider,
)
from app.rag.factory import build_knowledge_retriever
from app.tools.fitness import JavaFitnessTools
from app.workflows.fitness_agent import FitnessAgentWorkflow
from app.workflows.food_recognition import FoodRecognitionWorkflow
from app.workflows.workout_generation import WorkoutGenerationWorkflow


def create_app(settings: Settings | None = None) -> FastAPI:
    """创建 FastAPI 应用；允许测试注入独立配置，避免读取开发环境变量。"""

    resolved_settings = settings or get_settings()
    log_file = resolved_settings.log_file
    if log_file is None and resolved_settings.environment == "local":
        log_file = str(Path(__file__).resolve().parents[1] / "logs" / "fitness-agent.log")
    configure_logging(resolved_settings.log_level, log_file)

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        """记录服务生命周期；后续可在这里初始化和关闭共享客户端。"""

        if application.state.knowledge_retriever is not None:
            count = await application.state.knowledge_retriever.index(
                batch_size=resolved_settings.embedding_batch_size
            )
            logger.info(
                "knowledge_index_ready",
                extra={
                    "collection": resolved_settings.qdrant_collection,
                    "chunkCount": count,
                    "embeddingModel": resolved_settings.embedding_model,
                },
            )

        logger.info(
            "agent_service_started",
            extra={"provider": application.state.food_provider.name},
        )
        yield
        logger.info("agent_service_stopped")

    application = FastAPI(
        title="Fitness AI Agent",
        version="0.1.0",
        description="Internal Agent service for food vision and fitness workflows.",
        lifespan=lifespan,
    )
    # Provider 只在启动时构建一次，路由通过依赖注入使用统一实例。
    application.state.settings = resolved_settings
    application.state.food_provider = build_food_recognition_provider(resolved_settings)
    application.state.recipe_provider = build_recipe_generation_provider(resolved_settings)
    application.state.workout_provider = build_workout_generation_provider(resolved_settings)
    application.state.fitness_agent_provider = build_fitness_agent_provider(resolved_settings)
    application.state.knowledge_retriever = (
        build_knowledge_retriever(resolved_settings)
        if resolved_settings.knowledge_enabled
        else None
    )
    application.state.food_workflow = FoodRecognitionWorkflow(
        application.state.food_provider,
        application.state.recipe_provider,
        resolved_settings.recipe_generation_enabled,
    )
    application.state.workout_workflow = WorkoutGenerationWorkflow(
        application.state.workout_provider
    )
    application.state.fitness_agent_workflow = FitnessAgentWorkflow(
        application.state.fitness_agent_provider,
        JavaFitnessTools(
            resolved_settings,
            knowledge=application.state.knowledge_retriever,
        ),
        resolved_settings.max_tool_rounds,
    )
    application.add_middleware(RequestContextMiddleware)
    application.include_router(router)
    register_exception_handlers(application)
    return application


app = create_app()
