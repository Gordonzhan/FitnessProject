from __future__ import annotations

from enum import StrEnum
from typing import Annotated, Literal

from pydantic import AnyHttpUrl, Field

from app.schemas.base import ApiModel


class RecognitionMode(StrEnum):
    """识别结果当前只允许作为待确认草稿。"""

    DRAFT_ONLY = "DRAFT_ONLY"


class RecognitionStatus(StrEnum):
    """成功响应的识别状态；失败统一通过 HTTP 错误响应表达。"""

    SUCCEEDED = "SUCCEEDED"


class FoodUserContext(ApiModel):
    """辅助模型判断的最小用户上下文，不包含用户身份和健康隐私。"""

    goal: str | None = Field(default=None, max_length=40)
    daily_calorie_target: float | None = Field(default=None, ge=0, le=20_000)
    consumed_calories: float | None = Field(default=None, ge=0, le=50_000)
    dietary_restrictions: list[str] = Field(default_factory=list, max_length=20)


class FoodRecognitionRequest(ApiModel):
    """Java 后端提交给 Agent 的食物图片识别请求。"""

    request_id: str = Field(min_length=8, max_length=100, pattern=r"^[A-Za-z0-9._:-]+$")
    image_urls: list[AnyHttpUrl] = Field(min_length=1, max_length=3)
    user_context: FoodUserContext = Field(default_factory=FoodUserContext)
    dish_hint: str | None = Field(default=None, max_length=100)
    mode: RecognitionMode = RecognitionMode.DRAFT_ONLY


class NutritionEstimate(ApiModel):
    """模型估算的营养数据，不能直接作为最终入库值。"""

    calories_kcal: float = Field(ge=0, le=20_000)
    protein_grams: float = Field(ge=0, le=2_000)
    carbohydrate_grams: float = Field(ge=0, le=5_000)
    fat_grams: float = Field(ge=0, le=2_000)


WeightRange = Annotated[tuple[float, float], Field(min_length=2, max_length=2)]


class FoodEstimate(ApiModel):
    """单个可见食物的识别草稿。"""

    name: str = Field(min_length=1, max_length=100)
    estimated_weight_grams: float = Field(gt=0, le=10_000)
    weight_range_grams: WeightRange
    cooking_method: str | None = Field(default=None, max_length=50)
    nutrition: NutritionEstimate
    confidence: float = Field(ge=0, le=1)


RecipeStep = Annotated[str, Field(min_length=1, max_length=300)]


class RecipeSuggestion(ApiModel):
    """文本模型生成的可编辑菜谱建议，不代表图片中的真实制作过程。"""

    name: str = Field(min_length=1, max_length=100)
    steps: list[RecipeStep] = Field(min_length=1, max_length=12)
    confidence: float = Field(ge=0, le=1)
    provider: str = Field(min_length=1, max_length=40)
    disclaimer: str = Field(min_length=1, max_length=200)


class FoodRecognitionResponse(ApiModel):
    """返回 Java 后端的稳定食物识别契约。"""

    request_id: str
    status: RecognitionStatus
    provider: str
    foods: list[FoodEstimate]
    overall_confidence: float = Field(ge=0, le=1)
    needs_user_confirmation: bool = True
    warnings: list[str] = Field(default_factory=list)
    recipe_suggestion: RecipeSuggestion | None = None
    recipe_generation_status: Literal["SUCCEEDED", "FAILED", "SKIPPED"] = "SKIPPED"
