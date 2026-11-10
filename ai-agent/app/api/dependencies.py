from __future__ import annotations

import secrets
from typing import Annotated, cast

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.api.errors import AgentServiceError
from app.workflows.fitness_agent import FitnessAgentWorkflow
from app.workflows.food_recognition import FoodRecognitionWorkflow
from app.workflows.workout_generation import WorkoutGenerationWorkflow

bearer_scheme = HTTPBearer(auto_error=False)


def get_food_workflow(request: Request) -> FoodRecognitionWorkflow:
    """从应用状态中取得串联视觉和菜谱模型的工作流。"""

    return cast(FoodRecognitionWorkflow, request.app.state.food_workflow)


def get_workout_workflow(request: Request) -> WorkoutGenerationWorkflow:
    return cast(WorkoutGenerationWorkflow, request.app.state.workout_workflow)


def get_fitness_agent_workflow(request: Request) -> FitnessAgentWorkflow:
    return cast(FitnessAgentWorkflow, request.app.state.fitness_agent_workflow)


async def verify_service_token(
    request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
) -> None:
    """校验 Java 调用 Agent 时携带的服务令牌。"""

    expected_token: str = request.app.state.settings.service_token.get_secret_value()
    # 空令牌只允许本地开发；配置令牌后所有业务接口都会强制执行 Bearer 认证。
    if not expected_token:
        return
    if credentials is None:
        raise AgentServiceError(401, "AGENT_AUTH_REQUIRED", "缺少服务认证令牌")
    if credentials.scheme.lower() != "bearer" or not secrets.compare_digest(
        credentials.credentials, expected_token
    ):
        raise AgentServiceError(403, "AGENT_AUTH_REJECTED", "服务认证失败")


FoodWorkflowDependency = Annotated[FoodRecognitionWorkflow, Depends(get_food_workflow)]
WorkoutWorkflowDependency = Annotated[WorkoutGenerationWorkflow, Depends(get_workout_workflow)]
ServiceAuthDependency = Annotated[None, Depends(verify_service_token)]
FitnessAgentWorkflowDependency = Annotated[
    FitnessAgentWorkflow, Depends(get_fitness_agent_workflow)
]
