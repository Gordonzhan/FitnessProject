from __future__ import annotations

import json

import httpx
import pytest

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.providers.workout import OllamaWorkoutGenerationProvider
from app.schemas.workout import (
    WorkoutCandidate,
    WorkoutDraftExercise,
    WorkoutDraftRequest,
    WorkoutDraftResponse,
    WorkoutFocusRequirement,
    WorkoutPreferences,
    WorkoutTrainingIntent,
)
from app.workflows.workout_generation import WorkoutGenerationWorkflow


def request() -> WorkoutDraftRequest:
    return WorkoutDraftRequest(
        request_id="workflow-001",
        date="2026-10-08",
        user_request="今天练胸，30分钟",
        training_intent=WorkoutTrainingIntent(
            minimum_duration_minutes=20,
            maximum_duration_minutes=30,
            focus_requirements=[
                WorkoutFocusRequirement(region="CHEST", label="胸部", minimum_exercises=1)
            ],
        ),
        preferences=WorkoutPreferences(
            experience_level="BEGINNER",
            session_minutes=30,
            training_location="HOME",
            training_goal="GENERAL_FITNESS",
            equipment_tags=["无"],
            target_rpe_min=5,
            target_rpe_max=7,
        ),
        candidates=[
            WorkoutCandidate(
                exercise_code="EX_ONE",
                name="动作一",
                category="力量",
                primary_muscles="腿部",
                primary_region="LEGS",
                equipment="无",
                intensity="中",
                load_type="BODYWEIGHT",
            ),
            WorkoutCandidate(
                exercise_code="EX_TWO",
                name="动作二",
                category="力量",
                primary_muscles="胸部",
                primary_region="CHEST",
                equipment="无",
                intensity="中",
                load_type="BODYWEIGHT",
            ),
            WorkoutCandidate(
                exercise_code="EX_THREE",
                name="动作三",
                category="力量",
                primary_muscles="胸部",
                primary_region="CHEST",
                equipment="无",
                intensity="中",
                load_type="BODYWEIGHT",
            ),
        ],
    )


class InvalidProvider:
    @property
    def name(self) -> str:
        return "invalid"

    async def generate(self, value: WorkoutDraftRequest) -> WorkoutDraftResponse:
        return WorkoutDraftResponse(
            request_id=value.request_id,
            status="READY",
            provider=self.name,
            title="错误草稿",
            assistant_reply="测试回复",
            estimated_duration_minutes=20,
            target_rpe=6,
            exercises=[
                WorkoutDraftExercise(
                    exercise_code="EX_NOT_ALLOWED",
                    sets=3,
                    reps=10,
                    duration_seconds=40,
                    rest_seconds=60,
                    rpe=6,
                )
            ],
            disclaimer="test",
        )


class InvalidReplacementProvider:
    @property
    def name(self) -> str:
        return "invalid-replacement"

    async def generate(self, value: WorkoutDraftRequest) -> WorkoutDraftResponse:
        return WorkoutDraftResponse(
            request_id=value.request_id,
            status="READY",
            provider=self.name,
            title="错误替换草稿",
            assistant_reply="错误地改动了其他动作",
            estimated_duration_minutes=20,
            target_rpe=6,
            exercises=[
                WorkoutDraftExercise(
                    exercise_code="EX_FOUR",
                    sets=4,
                    reps=10,
                    duration_seconds=40,
                    rest_seconds=60,
                    rpe=6,
                ),
                WorkoutDraftExercise(
                    exercise_code="EX_TWO",
                    sets=4,
                    reps=10,
                    duration_seconds=40,
                    rest_seconds=60,
                    rpe=6,
                ),
            ],
            disclaimer="test",
        )


class OutOfRangeRpeProvider:
    @property
    def name(self) -> str:
        return "rpe-out-of-range"

    async def generate(self, value: WorkoutDraftRequest) -> WorkoutDraftResponse:
        return WorkoutDraftResponse(
            request_id=value.request_id,
            status="READY",
            provider=self.name,
            title="胸部训练",
            assistant_reply="测试回复",
            estimated_duration_minutes=20,
            target_rpe=9,
            exercises=[
                WorkoutDraftExercise(
                    exercise_code="EX_TWO",
                    sets=4,
                    reps=10,
                    duration_seconds=40,
                    rest_seconds=60,
                    rpe=8,
                ),
                WorkoutDraftExercise(
                    exercise_code="EX_THREE",
                    sets=4,
                    reps=10,
                    duration_seconds=40,
                    rest_seconds=60,
                    rpe=4,
                ),
            ],
            disclaimer="test",
        )


@pytest.mark.asyncio
async def test_ollama_invalid_draft_reports_safe_validation_fields() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        invalid_draft = {
            "requestId": "workflow-001",
            "status": "READY",
            "provider": "ollama",
            "title": "错误草稿",
            "assistantReply": "测试回复",
            "estimatedDurationMinutes": 20,
            "targetRpe": 6,
            "exercises": [
                {
                    "exerciseCode": "EX_TWO",
                    "sets": "不是数字",
                    "reps": 10,
                    "durationSeconds": 40,
                    "restSeconds": 60,
                    "rpe": 6,
                }
            ],
            "disclaimer": "test",
        }
        return httpx.Response(
            200,
            json={"message": {"content": json.dumps(invalid_draft, ensure_ascii=False)}},
        )

    provider = OllamaWorkoutGenerationProvider(
        Settings(
            environment="test",
            provider="ollama",
            ollama_base_url="http://ollama.test",
        ),
        httpx.MockTransport(handler),
    )

    with pytest.raises(AgentServiceError) as captured:
        await provider.generate(request())

    assert captured.value.code == "WORKOUT_MODEL_DRAFT_INVALID"
    assert captured.value.diagnostic_details == [
        {
            "field": "draft.exercises.0.sets",
            "type": "int_parsing",
            "message": "Input should be a valid integer, unable to parse string as an integer",
        }
    ]


@pytest.mark.asyncio
async def test_out_of_range_rpe_is_normalized_without_fake_fallback() -> None:
    draft = await WorkoutGenerationWorkflow(OutOfRangeRpeProvider()).create(request())

    assert draft.status == "READY"
    assert draft.provider == "rpe-out-of-range"
    assert draft.target_rpe == 7
    assert [item.rpe for item in draft.exercises] == [7, 5]
    assert any("RPE 范围自动调整" in warning for warning in draft.warnings)


@pytest.mark.asyncio
async def test_invalid_provider_output_falls_back_to_candidate_only_draft() -> None:
    draft = await WorkoutGenerationWorkflow(InvalidProvider()).create(request())

    assert draft.status == "FALLBACK"
    assert {item.exercise_code for item in draft.exercises} == {"EX_TWO", "EX_THREE"}
    assert all(item.sets == 4 for item in draft.exercises)
    duration_seconds = sum(
        item.duration_seconds * item.sets + item.rest_seconds * max(0, item.sets - 1)
        for item in draft.exercises
    )
    assert duration_seconds < 20 * 60
    assert any("降级草稿" in warning for warning in draft.warnings)


@pytest.mark.asyncio
async def test_invalid_replacement_falls_back_to_same_region_and_preserves_other_actions() -> None:
    value = request()
    extra = WorkoutCandidate(
        exercise_code="EX_FOUR",
        name="动作四",
        category="力量",
        primary_muscles="胸部",
        primary_region="CHEST",
        equipment="无",
        intensity="中",
        load_type="BODYWEIGHT",
    )
    current = [
        WorkoutDraftExercise(
            exercise_code=code,
            sets=4,
            reps=10,
            duration_seconds=40,
            rest_seconds=60,
            rpe=6,
        )
        for code in ("EX_TWO", "EX_THREE")
    ]
    replacement = value.model_copy(
        update={
            "candidates": [*value.candidates, extra],
            "current_exercises": current,
            "replace_exercise_code": "EX_TWO",
        }
    )

    draft = await WorkoutGenerationWorkflow(InvalidReplacementProvider()).create(replacement)

    assert draft.status == "FALLBACK"
    assert [item.exercise_code for item in draft.exercises] == ["EX_FOUR", "EX_THREE"]


def test_excluded_primary_region_is_rejected_even_when_candidate_is_present() -> None:
    value = request()
    guarded = value.model_copy(
        update={
            "training_intent": value.training_intent.model_copy(
                update={"excluded_regions": ["CHEST"]}
            )
        }
    )
    draft = WorkoutDraftResponse(
        request_id=value.request_id,
        status="READY",
        provider="invalid",
        title="不应通过的草稿",
        assistant_reply="错误地包含胸部动作",
        estimated_duration_minutes=20,
        target_rpe=6,
        exercises=[
            WorkoutDraftExercise(
                exercise_code=code,
                sets=4,
                reps=10,
                duration_seconds=40,
                rest_seconds=60,
                rpe=6,
            )
            for code in ("EX_TWO", "EX_THREE")
        ],
        disclaimer="test",
    )

    assert (
        WorkoutGenerationWorkflow(InvalidProvider())._validation_error(draft, guarded)
        == "excluded_region_validation_failed"
    )
