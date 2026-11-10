from __future__ import annotations

from app.providers.base import FoodRecognitionProvider, RecipeGenerationProvider
from app.schemas.food import (
    FoodEstimate,
    FoodRecognitionRequest,
    FoodRecognitionResponse,
    NutritionEstimate,
    RecipeSuggestion,
    RecognitionStatus,
)
from app.schemas.workout import (
    WorkoutCandidate,
    WorkoutDraftExercise,
    WorkoutDraftRequest,
    WorkoutDraftResponse,
)


class FakeFoodRecognitionProvider(FoodRecognitionProvider):
    """返回固定结果的测试 Provider，不访问图片或任何外部模型。"""

    @property
    def name(self) -> str:
        return "fake"

    async def recognize(self, request: FoodRecognitionRequest) -> FoodRecognitionResponse:
        """生成可重复的鸡胸肉结果，用于接口和 Java 联调测试。"""

        return FoodRecognitionResponse(
            request_id=request.request_id,
            status=RecognitionStatus.SUCCEEDED,
            provider=self.name,
            foods=[
                FoodEstimate(
                    name="鸡胸肉",
                    estimated_weight_grams=150,
                    weight_range_grams=(120, 180),
                    cooking_method="煎制",
                    nutrition=NutritionEstimate(
                        calories_kcal=248,
                        protein_grams=46.5,
                        carbohydrate_grams=0,
                        fat_grams=5.4,
                    ),
                    confidence=0.82,
                )
            ],
            overall_confidence=0.82,
            needs_user_confirmation=True,
            warnings=["当前为 Fake Provider 模拟结果，不代表图片真实识别结果"],
        )


class FakeRecipeGenerationProvider(RecipeGenerationProvider):
    """返回稳定菜谱建议，覆盖工作流和 Java 契约测试而不访问模型。"""

    @property
    def name(self) -> str:
        return "fake"

    async def generate(
        self, request: FoodRecognitionRequest, foods: list[FoodEstimate]
    ) -> RecipeSuggestion:
        return RecipeSuggestion(
            name=request.dish_hint or "香煎鸡胸肉",
            steps=["鸡胸肉擦干并调味。", "平底锅加热后煎至熟透，静置后切片。"],
            confidence=0.72,
            provider=self.name,
            disclaimer="制作步骤由 AI 根据图片识别结果推测，请按实际情况修改。",
        )


class FakeWorkoutGenerationProvider:
    """使用候选动作生成稳定草稿，供本地端到端验收和自动化测试。"""

    @property
    def name(self) -> str:
        return "fake"

    async def generate(self, request: WorkoutDraftRequest) -> WorkoutDraftResponse:
        selected = self._select_candidates(request)
        exercises = [self._prescription(item, request) for item in selected]
        if request.replace_exercise_code and request.current_exercises:
            if exercises:
                replacement = exercises[0]
                exercises = [
                    replacement if item.exercise_code == request.replace_exercise_code else item
                    for item in request.current_exercises
                ]
            else:
                exercises = list(request.current_exercises)
        return WorkoutDraftResponse(
            request_id=request.request_id,
            status="READY",
            provider=self.name,
            title=self._title(request),
            assistant_reply=(
                f"我按“{request.user_request[:80]}”安排了这次训练，并同时遵守你保存的器械、"
                "排除动作、身体限制和目标 RPE。"
            ),
            estimated_duration_minutes=min(
                request.preferences.session_minutes,
                max(10, len(exercises) * 8),
            ),
            target_rpe=(request.preferences.target_rpe_min + request.preferences.target_rpe_max)
            // 2,
            exercises=exercises,
            warnings=["当前为 Fake Provider 生成结果，仅用于本地流程验收"],
            disclaimer="训练草稿仅供健身参考，请根据现场状态调整，并在确认后才保存。",
        )

    def _select_candidates(self, request: WorkoutDraftRequest) -> list[WorkoutCandidate]:
        current_codes = {item.exercise_code for item in request.current_exercises}
        if request.replace_exercise_code:
            candidates_by_code = {item.exercise_code: item for item in request.candidates}
            replaced = candidates_by_code.get(request.replace_exercise_code)
            return [
                item
                for item in request.candidates
                if item.exercise_code not in current_codes
                and item.exercise_code != request.replace_exercise_code
                and replaced is not None
                and item.primary_region == replaced.primary_region
                and not self._is_excluded(item, request)
            ][:1]
        count = max(2, min(12, request.training_intent.maximum_duration_minutes // 8))
        selected: list[WorkoutCandidate] = []
        for requirement in request.training_intent.focus_requirements:
            matching = [
                item
                for item in request.candidates
                if item not in selected
                and not self._is_excluded(item, request)
                and self._region_matches(requirement.region, item.primary_region)
            ]
            selected.extend(matching[: requirement.minimum_exercises])
        for candidate in request.candidates:
            if len(selected) >= count:
                break
            if self._is_excluded(candidate, request):
                continue
            if request.training_intent.focus_requirements and not any(
                self._region_matches(requirement.region, candidate.primary_region)
                for requirement in request.training_intent.focus_requirements
            ):
                continue
            if candidate in selected or not self._within_region_maximum(
                candidate, selected, request
            ):
                continue
            selected.append(candidate)
        return selected

    def _prescription(
        self, candidate: WorkoutCandidate, request: WorkoutDraftRequest
    ) -> WorkoutDraftExercise:
        rpe = min(
            max(
                candidate.default_rpe or request.preferences.target_rpe_min,
                request.preferences.target_rpe_min,
            ),
            request.preferences.target_rpe_max,
        )
        return WorkoutDraftExercise(
            exercise_code=candidate.exercise_code,
            sets=4,
            reps=candidate.default_reps or 10,
            duration_seconds=candidate.default_duration_seconds or 45,
            rest_seconds=candidate.default_rest_seconds or 60,
            rpe=rpe,
            weight=candidate.recent_weight_kg,
            rationale="符合当前器械、限制和训练目标",
        )

    @staticmethod
    def _title(request: WorkoutDraftRequest) -> str:
        labels = [item.label for item in request.training_intent.focus_requirements]
        if labels:
            return "与".join(dict.fromkeys(labels)) + "专项训练"
        goal = request.preferences.training_goal
        return {
            "FAT_LOSS": "全身燃脂训练",
            "MUSCLE_GAIN": "增肌基础训练",
            "STRENGTH": "力量进阶训练",
            "ENDURANCE": "耐力循环训练",
            "MOBILITY": "灵活性训练",
        }.get(goal, "今日综合训练")

    def _is_excluded(
        self, candidate: WorkoutCandidate, request: WorkoutDraftRequest
    ) -> bool:
        return any(
            self._region_matches(region, candidate.primary_region)
            for region in request.training_intent.excluded_regions
        )

    @staticmethod
    def _region_matches(required: str, actual: str) -> bool:
        return actual in {"BICEPS", "TRICEPS"} if required == "ARMS" else required == actual

    def _within_region_maximum(
        self,
        candidate: WorkoutCandidate,
        selected: list[WorkoutCandidate],
        request: WorkoutDraftRequest,
    ) -> bool:
        for requirement in request.training_intent.focus_requirements:
            if requirement.maximum_exercises is None:
                continue
            if not self._region_matches(requirement.region, candidate.primary_region):
                continue
            current = sum(
                self._region_matches(requirement.region, item.primary_region) for item in selected
            )
            if current >= requirement.maximum_exercises:
                return False
        return True
