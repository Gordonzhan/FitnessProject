from __future__ import annotations

import pytest

from app.api.errors import AgentServiceError
from app.providers.fake import FakeFoodRecognitionProvider
from app.schemas.food import FoodEstimate, FoodRecognitionRequest, RecipeSuggestion
from app.workflows.food_recognition import FoodRecognitionWorkflow


class FailingRecipeProvider:
    """模拟第二个模型失败，验证增强能力不会拖垮识图主流程。"""

    @property
    def name(self) -> str:
        return "failing-test"

    async def generate(
        self, request: FoodRecognitionRequest, foods: list[FoodEstimate]
    ) -> RecipeSuggestion:
        raise AgentServiceError(504, "RECIPE_MODEL_TIMEOUT", "timeout")


def request() -> FoodRecognitionRequest:
    return FoodRecognitionRequest.model_validate(
        {
            "requestId": "workflow-test-001",
            "imageUrls": ["https://example.com/meal.jpg"],
        }
    )


@pytest.mark.asyncio
async def test_recipe_failure_keeps_food_recognition_result() -> None:
    workflow = FoodRecognitionWorkflow(
        FakeFoodRecognitionProvider(), FailingRecipeProvider(), recipe_generation_enabled=True
    )

    response = await workflow.recognize(request())

    assert response.status == "SUCCEEDED"
    assert response.foods[0].name == "鸡胸肉"
    assert response.recipe_suggestion is None
    assert response.recipe_generation_status == "FAILED"
    assert "可手动填写" in response.warnings[-1]
