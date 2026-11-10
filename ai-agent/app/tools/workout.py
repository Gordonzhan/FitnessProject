from __future__ import annotations

from app.providers.base import WorkoutGenerationProvider
from app.schemas.workout import WorkoutDraftRequest, WorkoutDraftResponse


async def create_workout_draft(
    provider: WorkoutGenerationProvider, request: WorkoutDraftRequest
) -> WorkoutDraftResponse:
    """训练生成 Function Tool；输入输出都由 Pydantic Schema 强校验。"""

    return await provider.generate(request)
