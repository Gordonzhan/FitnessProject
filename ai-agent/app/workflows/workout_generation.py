from __future__ import annotations

import time
from typing import TypedDict, cast

from langgraph.graph import END, START, StateGraph

from app.api.errors import AgentServiceError
from app.core.logging import logger
from app.providers.base import WorkoutGenerationProvider
from app.providers.fake import FakeWorkoutGenerationProvider
from app.schemas.workout import WorkoutDraftRequest, WorkoutDraftResponse
from app.tools.workout import create_workout_draft


class WorkoutState(TypedDict, total=False):
    request: WorkoutDraftRequest
    draft: WorkoutDraftResponse
    error: str


class WorkoutGenerationWorkflow:
    """生成、结构校验与失败降级组成的最小 LangGraph 工作流。"""

    def __init__(self, provider: WorkoutGenerationProvider) -> None:
        self._provider = provider
        graph = StateGraph(WorkoutState)
        graph.add_node("generate", self._generate)
        graph.add_node("validate", self._validate)
        graph.add_node("fallback", self._fallback)
        graph.add_edge(START, "generate")
        graph.add_edge("generate", "validate")
        graph.add_conditional_edges(
            "validate", self._route_after_validation, {"ready": END, "fallback": "fallback"}
        )
        graph.add_edge("fallback", END)
        self._graph = graph.compile()

    async def create(self, request: WorkoutDraftRequest) -> WorkoutDraftResponse:
        state = cast(WorkoutState, await self._graph.ainvoke({"request": request}))
        draft = state.get("draft")
        if draft is None:
            raise RuntimeError("workout workflow completed without a draft")
        return draft

    async def _generate(self, state: WorkoutState) -> WorkoutState:
        started = time.perf_counter()
        try:
            draft = await create_workout_draft(self._provider, state["request"])
            draft = self._normalize_rpe(draft, state["request"])
            logger.info(
                "workout_draft_phase_completed",
                extra={
                    "phase": "workout_model",
                    "provider": self._provider.name,
                    "candidateCount": len(state["request"].candidates),
                    "durationMs": round((time.perf_counter() - started) * 1000, 2),
                },
            )
            return {"draft": draft}
        except Exception as error:  # Provider 故障进入确定性降级，不暴露上游细节。
            error_code = (
                error.code
                if isinstance(error, AgentServiceError)
                else error.__class__.__name__
            )
            validation_details = (
                error.diagnostic_details if isinstance(error, AgentServiceError) else []
            )
            logger.warning(
                "workout_draft_provider_failed",
                extra={
                    "phase": "workout_model",
                    "provider": self._provider.name,
                    "candidateCount": len(state["request"].candidates),
                    "durationMs": round((time.perf_counter() - started) * 1000, 2),
                    "errorCode": error_code,
                    "validationDetails": validation_details,
                },
            )
            return {"error": error_code}

    @staticmethod
    def _normalize_rpe(
        draft: WorkoutDraftResponse, request: WorkoutDraftRequest
    ) -> WorkoutDraftResponse:
        """把模型强度收敛到用户偏好边界；其他结构和安全错误仍交由校验拒绝。"""

        minimum = request.preferences.target_rpe_min
        maximum = request.preferences.target_rpe_max
        normalized_exercises = []
        adjustments: list[dict[str, object]] = []
        for exercise in draft.exercises:
            normalized_rpe = min(max(exercise.rpe, minimum), maximum)
            normalized_exercises.append(exercise.model_copy(update={"rpe": normalized_rpe}))
            if normalized_rpe != exercise.rpe:
                adjustments.append(
                    {
                        "field": "exercises.rpe",
                        "exerciseCode": exercise.exercise_code,
                        "original": exercise.rpe,
                        "normalized": normalized_rpe,
                    }
                )

        normalized_target = min(max(draft.target_rpe, minimum), maximum)
        if normalized_target != draft.target_rpe:
            adjustments.append(
                {
                    "field": "targetRpe",
                    "original": draft.target_rpe,
                    "normalized": normalized_target,
                }
            )
        if not adjustments:
            return draft

        logger.info(
            "workout_draft_rpe_normalized",
            extra={"validationDetails": adjustments},
        )
        warnings = list(draft.warnings)
        warning = "模型建议强度已按你的目标 RPE 范围自动调整"
        if warning not in warnings and len(warnings) < 20:
            warnings.append(warning)
        return draft.model_copy(
            update={
                "target_rpe": normalized_target,
                "exercises": normalized_exercises,
                "warnings": warnings,
            }
        )

    async def _validate(self, state: WorkoutState) -> WorkoutState:
        draft = state.get("draft")
        if draft is None:
            return state
        request = state["request"]
        error = self._validation_error(draft, request)
        if error:
            logger.warning(
                "workout_draft_validation_failed",
                extra={
                    "errorCode": error,
                    "validationDetails": self._validation_details(draft, request, error),
                },
            )
        return {"error": error} if error else state

    def _validation_error(
        self, draft: WorkoutDraftResponse, request: WorkoutDraftRequest
    ) -> str | None:
        candidates = {item.exercise_code: item for item in request.candidates}
        candidate_codes = set(candidates)
        codes = [item.exercise_code for item in draft.exercises]
        if len(codes) < 2 or len(codes) > 12:
            return "exercise_count_validation_failed"
        if len(codes) != len(set(codes)) or not set(codes).issubset(candidate_codes):
            return "candidate_validation_failed"
        if any(
            item.rpe < request.preferences.target_rpe_min
            or item.rpe > request.preferences.target_rpe_max
            for item in draft.exercises
        ):
            return "rpe_validation_failed"
        intent = request.training_intent
        if any(
            self._region_matches(excluded, candidates[item.exercise_code].primary_region)
            for item in draft.exercises
            for excluded in intent.excluded_regions
        ):
            return "excluded_region_validation_failed"
        for requirement in intent.focus_requirements:
            count = sum(
                self._region_matches(
                    requirement.region,
                    candidates[item.exercise_code].primary_region,
                )
                for item in draft.exercises
            )
            if count < requirement.minimum_exercises or (
                requirement.maximum_exercises is not None and count > requirement.maximum_exercises
            ):
                return "focus_requirement_validation_failed"
        if intent.focus_requirements and any(
            not any(
                self._region_matches(
                    requirement.region,
                    candidates[item.exercise_code].primary_region,
                )
                for requirement in intent.focus_requirements
            )
            for item in draft.exercises
        ):
            return "unrelated_focus_validation_failed"
        if request.replace_exercise_code and request.current_exercises:
            current_codes = [item.exercise_code for item in request.current_exercises]
            if len(codes) != len(current_codes):
                return "replacement_count_validation_failed"
            for before, after in zip(current_codes, codes, strict=True):
                if before == request.replace_exercise_code:
                    if after == before:
                        return "replacement_unchanged_validation_failed"
                elif after != before:
                    return "replacement_modified_other_exercise"
        return None

    def _validation_details(
        self,
        draft: WorkoutDraftResponse,
        request: WorkoutDraftRequest,
        error_code: str,
    ) -> list[dict[str, object]]:
        """记录确定性规则失败的位置和值，不记录用户文本或完整模型响应。"""

        candidates = {item.exercise_code: item for item in request.candidates}
        codes = [item.exercise_code for item in draft.exercises]
        if error_code == "exercise_count_validation_failed":
            return [
                {
                    "field": "exercises",
                    "actualCount": len(codes),
                    "minimum": 2,
                    "maximum": 12,
                }
            ]
        if error_code == "candidate_validation_failed":
            duplicates = sorted({code for code in codes if codes.count(code) > 1})
            unknown = sorted(set(codes) - set(candidates))
            return [
                {
                    "field": "exercises.exerciseCode",
                    "duplicateCodes": duplicates,
                    "unknownCodes": unknown,
                }
            ]
        if error_code == "rpe_validation_failed":
            invalid = [
                {"exerciseCode": item.exercise_code, "rpe": item.rpe}
                for item in draft.exercises
                if item.rpe < request.preferences.target_rpe_min
                or item.rpe > request.preferences.target_rpe_max
            ]
            return [
                {
                    "field": "exercises.rpe",
                    "minimum": request.preferences.target_rpe_min,
                    "maximum": request.preferences.target_rpe_max,
                    "invalidExercises": invalid,
                }
            ]
        if error_code == "excluded_region_validation_failed":
            invalid = [
                {
                    "exerciseCode": item.exercise_code,
                    "region": candidates[item.exercise_code].primary_region,
                }
                for item in draft.exercises
                if any(
                    self._region_matches(
                        excluded, candidates[item.exercise_code].primary_region
                    )
                    for excluded in request.training_intent.excluded_regions
                )
            ]
            return [{"field": "exercises.primaryRegion", "invalidExercises": invalid}]
        if error_code == "focus_requirement_validation_failed":
            failures: list[dict[str, object]] = []
            for requirement in request.training_intent.focus_requirements:
                count = sum(
                    self._region_matches(requirement.region, candidates[code].primary_region)
                    for code in codes
                )
                if count < requirement.minimum_exercises or (
                    requirement.maximum_exercises is not None
                    and count > requirement.maximum_exercises
                ):
                    failures.append(
                        {
                            "region": requirement.region,
                            "actualCount": count,
                            "minimum": requirement.minimum_exercises,
                            "maximum": requirement.maximum_exercises,
                        }
                    )
            return [{"field": "trainingIntent.focusRequirements", "failures": failures}]
        if error_code == "unrelated_focus_validation_failed":
            invalid = [
                {"exerciseCode": code, "region": candidates[code].primary_region}
                for code in codes
                if not any(
                    self._region_matches(
                        requirement.region, candidates[code].primary_region
                    )
                    for requirement in request.training_intent.focus_requirements
                )
            ]
            return [{"field": "exercises.primaryRegion", "unrelatedExercises": invalid}]
        if error_code.startswith("replacement_"):
            return [
                {
                    "field": "exercises",
                    "replaceExerciseCode": request.replace_exercise_code,
                    "currentCodes": [
                        item.exercise_code for item in request.current_exercises
                    ],
                    "generatedCodes": codes,
                }
            ]
        return [{"field": "workoutDraft", "type": error_code}]

    @staticmethod
    def _region_matches(required: str, actual: str) -> bool:
        return actual in {"BICEPS", "TRICEPS"} if required == "ARMS" else required == actual

    @staticmethod
    def _route_after_validation(state: WorkoutState) -> str:
        return "ready" if state.get("draft") is not None and not state.get("error") else "fallback"

    async def _fallback(self, state: WorkoutState) -> WorkoutState:
        started = time.perf_counter()
        logger.warning(
            "workout_draft_fallback_started",
            extra={"errorCode": state.get("error", "provider_failed")},
        )
        draft = await FakeWorkoutGenerationProvider().generate(state["request"])
        error = self._validation_error(draft, state["request"])
        if error:
            raise RuntimeError(f"fallback workout draft failed validation: {error}")
        warnings = list(draft.warnings)
        warnings.append("模型结果不可用，已按动作库默认值生成安全降级草稿")
        logger.info(
            "workout_draft_phase_completed",
            extra={
                "phase": "deterministic_fallback",
                "provider": "fake",
                "candidateCount": len(state["request"].candidates),
                "durationMs": round((time.perf_counter() - started) * 1000, 2),
            },
        )
        return {"draft": draft.model_copy(update={"status": "FALLBACK", "warnings": warnings})}
