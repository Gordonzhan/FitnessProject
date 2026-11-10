from __future__ import annotations

from datetime import date
from decimal import Decimal
from typing import Literal

from pydantic import Field, model_validator

from app.schemas.base import ApiModel


class WorkoutPreferences(ApiModel):
    experience_level: str = Field(min_length=1, max_length=30)
    session_minutes: int = Field(ge=10, le=180)
    training_location: str = Field(min_length=1, max_length=30)
    training_goal: str = Field(min_length=1, max_length=30)
    equipment_tags: list[str] = Field(default_factory=list, max_length=20)
    limitation_tags: list[str] = Field(default_factory=list, max_length=10)
    preferred_exercise_codes: list[str] = Field(default_factory=list, max_length=100)
    excluded_exercise_codes: list[str] = Field(default_factory=list, max_length=100)
    target_rpe_min: int = Field(ge=1, le=10)
    target_rpe_max: int = Field(ge=1, le=10)

    @model_validator(mode="after")
    def validate_rpe(self) -> WorkoutPreferences:
        if self.target_rpe_min > self.target_rpe_max:
            raise ValueError("targetRpeMin cannot exceed targetRpeMax")
        return self


class WorkoutCandidate(ApiModel):
    exercise_code: str = Field(pattern=r"^[A-Z0-9_]{1,32}$")
    name: str = Field(min_length=1, max_length=50)
    category: str = Field(min_length=1, max_length=30)
    primary_muscles: str = Field(min_length=1, max_length=100)
    primary_region: str = Field(min_length=1, max_length=30)
    equipment: str = Field(min_length=1, max_length=50)
    intensity: str = Field(min_length=1, max_length=20)
    default_sets: int | None = Field(default=None, ge=1, le=10)
    default_reps: int | None = Field(default=None, ge=1, le=100)
    default_duration_seconds: int | None = Field(default=None, ge=10, le=1800)
    default_rest_seconds: int | None = Field(default=None, ge=0, le=600)
    default_rpe: int | None = Field(default=None, ge=1, le=10)
    load_type: Literal["BODYWEIGHT", "NO_LOAD", "OPTIONAL_LOAD", "EXTERNAL_LOAD", "ASSISTANCE"]
    recent_weight_kg: Decimal | None = Field(default=None, gt=0, le=1000)


class WorkoutDraftExercise(ApiModel):
    exercise_code: str = Field(pattern=r"^[A-Z0-9_]{1,32}$")
    sets: int = Field(ge=1, le=10)
    reps: int = Field(ge=1, le=100)
    duration_seconds: int = Field(ge=10, le=1800)
    rest_seconds: int = Field(ge=0, le=600)
    rpe: int = Field(ge=1, le=10)
    weight: Decimal | None = Field(default=None, gt=0, le=1000)
    rationale: str = Field(default="", max_length=120)


class WorkoutFocusRequirement(ApiModel):
    region: str = Field(min_length=1, max_length=30)
    label: str = Field(min_length=1, max_length=30)
    minimum_exercises: int = Field(ge=1, le=12)
    maximum_exercises: int | None = Field(default=None, ge=1, le=12)


class WorkoutTrainingIntent(ApiModel):
    minimum_duration_minutes: int | None = Field(default=None, ge=10, le=180)
    maximum_duration_minutes: int = Field(ge=10, le=180)
    focus_requirements: list[WorkoutFocusRequirement] = Field(default_factory=list, max_length=10)
    excluded_regions: list[str] = Field(default_factory=list, max_length=10)


class WorkoutDraftRequest(ApiModel):
    request_id: str = Field(min_length=1, max_length=100)
    date: date
    user_request: str = Field(min_length=1, max_length=500)
    training_intent: WorkoutTrainingIntent
    preferences: WorkoutPreferences
    candidates: list[WorkoutCandidate] = Field(min_length=1, max_length=100)
    current_exercises: list[WorkoutDraftExercise] = Field(default_factory=list, max_length=12)
    replace_exercise_code: str | None = Field(default=None, pattern=r"^[A-Z0-9_]{1,32}$")
    validation_issues: list[str] = Field(default_factory=list, max_length=20)
    mode: Literal["DRAFT_ONLY"] = "DRAFT_ONLY"


class WorkoutDraftResponse(ApiModel):
    request_id: str
    status: Literal["READY", "FALLBACK"]
    provider: str
    title: str = Field(min_length=1, max_length=80)
    assistant_reply: str = Field(min_length=1, max_length=300)
    estimated_duration_minutes: int = Field(ge=1, le=180)
    target_rpe: int = Field(ge=1, le=10)
    exercises: list[WorkoutDraftExercise] = Field(min_length=2, max_length=12)
    warnings: list[str] = Field(default_factory=list, max_length=20)
    disclaimer: str = Field(min_length=1, max_length=300)
