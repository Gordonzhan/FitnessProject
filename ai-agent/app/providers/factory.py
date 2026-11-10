from __future__ import annotations

from app.core.config import Settings
from app.providers.base import (
    FoodRecognitionProvider,
    RecipeGenerationProvider,
    WorkoutGenerationProvider,
)
from app.providers.fake import (
    FakeFoodRecognitionProvider,
    FakeRecipeGenerationProvider,
    FakeWorkoutGenerationProvider,
)
from app.providers.ollama import OllamaFoodRecognitionProvider, OllamaRecipeGenerationProvider
from app.providers.workout import OllamaWorkoutGenerationProvider


def build_food_recognition_provider(settings: Settings) -> FoodRecognitionProvider:
    """集中选择 Provider，避免 API 路由直接依赖某个模型供应商。"""

    if settings.provider == "fake":
        return FakeFoodRecognitionProvider()
    if settings.provider == "ollama":
        return OllamaFoodRecognitionProvider(settings)
    raise ValueError(f"Unsupported Agent provider: {settings.provider}")


def build_recipe_generation_provider(settings: Settings) -> RecipeGenerationProvider:
    """菜谱模型可独立切换；未配置时沿用视觉 Provider，降低本地配置成本。"""

    provider = settings.recipe_provider or settings.provider
    if provider == "fake":
        return FakeRecipeGenerationProvider()
    if provider == "ollama":
        return OllamaRecipeGenerationProvider(settings)
    raise ValueError(f"Unsupported recipe provider: {provider}")


def build_workout_generation_provider(settings: Settings) -> WorkoutGenerationProvider:
    """训练生成使用独立文本模型配置，未设置时沿用全局 Provider。"""

    provider = settings.workout_provider or settings.provider
    if provider == "fake":
        return FakeWorkoutGenerationProvider()
    if provider == "ollama":
        return OllamaWorkoutGenerationProvider(settings)
    raise ValueError(f"Unsupported workout provider: {provider}")
