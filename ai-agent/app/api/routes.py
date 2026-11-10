from __future__ import annotations

from fastapi import APIRouter, Request

from app.api.dependencies import (
    FitnessAgentWorkflowDependency,
    FoodWorkflowDependency,
    ServiceAuthDependency,
    WorkoutWorkflowDependency,
)
from app.schemas.agent import FitnessAgentRequest, FitnessAgentResponse
from app.schemas.common import HealthResponse
from app.schemas.food import FoodRecognitionRequest, FoodRecognitionResponse
from app.schemas.workout import WorkoutDraftRequest, WorkoutDraftResponse

router = APIRouter()


@router.get("/health", response_model=HealthResponse, tags=["system"])
async def health(request: Request) -> HealthResponse:
    """返回进程状态和当前 Provider，供部署探针与联调检查使用。"""

    return HealthResponse(provider=request.app.state.food_provider.name)


@router.post(
    "/v1/vision/food-recognition",
    response_model=FoodRecognitionResponse,
    tags=["food-vision"],
)
async def recognize_food(
    payload: FoodRecognitionRequest,
    workflow: FoodWorkflowDependency,
    _auth: ServiceAuthDependency,
) -> FoodRecognitionResponse:
    """接收 Java 请求，依次完成视觉识别和可降级的菜谱文案生成。"""

    return await workflow.recognize(payload)


@router.post(
    "/v1/workouts/draft",
    response_model=WorkoutDraftResponse,
    tags=["workout"],
)
async def create_workout_draft_route(
    payload: WorkoutDraftRequest,
    workflow: WorkoutWorkflowDependency,
    _auth: ServiceAuthDependency,
) -> WorkoutDraftResponse:
    """生成训练草稿；结果仍需 Java 确定性校验，不能直接持久化。"""

    return await workflow.create(payload)


@router.post("/v1/agents/fitness/run", response_model=FitnessAgentResponse, tags=["agent"])
async def run_fitness_agent(
    payload: FitnessAgentRequest,
    workflow: FitnessAgentWorkflowDependency,
    _auth: ServiceAuthDependency,
) -> FitnessAgentResponse:
    return await workflow.run(payload)
