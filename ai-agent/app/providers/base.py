from __future__ import annotations

from typing import Protocol

from app.schemas.food import (
    FoodEstimate,
    FoodRecognitionRequest,
    FoodRecognitionResponse,
    RecipeSuggestion,
)
from app.schemas.workout import WorkoutDraftRequest, WorkoutDraftResponse


class FoodRecognitionProvider(Protocol):
    """食物识别能力契约，使 Fake、Ollama 和云模型可以无缝替换。"""

    @property
    def name(self) -> str: ...

    async def recognize(self, request: FoodRecognitionRequest) -> FoodRecognitionResponse: ...


class RecipeGenerationProvider(Protocol):
    """根据已校验的视觉结果生成菜名和步骤，与营养计算保持隔离。"""

    @property
    def name(self) -> str: ...

    async def generate(
        self, request: FoodRecognitionRequest, foods: list[FoodEstimate]
    ) -> RecipeSuggestion: ...


class WorkoutGenerationProvider(Protocol):
    """根据 Java 提供的合规动作候选生成训练草稿，不自行访问业务数据。"""

    @property
    def name(self) -> str: ...

    async def generate(self, request: WorkoutDraftRequest) -> WorkoutDraftResponse: ...
