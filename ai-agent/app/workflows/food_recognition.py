from __future__ import annotations

from app.api.errors import AgentServiceError
from app.core.logging import logger
from app.providers.base import FoodRecognitionProvider, RecipeGenerationProvider
from app.schemas.food import FoodRecognitionRequest, FoodRecognitionResponse


class FoodRecognitionWorkflow:
    """先识图再生成菜谱文案；文案失败不得破坏已有食材识别结果。"""

    def __init__(
        self,
        vision_provider: FoodRecognitionProvider,
        recipe_provider: RecipeGenerationProvider,
        recipe_generation_enabled: bool,
    ) -> None:
        self._vision_provider = vision_provider
        self._recipe_provider = recipe_provider
        self._recipe_generation_enabled = recipe_generation_enabled

    async def recognize(self, request: FoodRecognitionRequest) -> FoodRecognitionResponse:
        recognition = await self._vision_provider.recognize(request)
        if not self._recipe_generation_enabled or not recognition.foods:
            return recognition
        try:
            suggestion = await self._recipe_provider.generate(request, recognition.foods)
            return recognition.model_copy(
                update={"recipe_suggestion": suggestion, "recipe_generation_status": "SUCCEEDED"}
            )
        except AgentServiceError as error:
            # 菜谱文案属于增强能力；失败时保留识别草稿，让用户仍可手工填写。
            logger.warning(
                "recipe_generation_degraded",
                extra={"provider": self._recipe_provider.name, "errorType": error.code},
            )
            warning = "AI 菜名和步骤生成失败，已保留食材识别结果，可手动填写"
            return recognition.model_copy(
                update={
                    "warnings": [*recognition.warnings, warning],
                    "recipe_generation_status": "FAILED",
                }
            )
