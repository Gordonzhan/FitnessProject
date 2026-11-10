from __future__ import annotations

import json
from typing import Any

import httpx
from pydantic import BaseModel, ConfigDict, ValidationError

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.schemas.workout import WorkoutDraftRequest, WorkoutDraftResponse


class OllamaMessage(BaseModel):
    model_config = ConfigDict(extra="ignore")
    content: str


class OllamaChatResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")
    message: OllamaMessage


class OllamaWorkoutGenerationProvider:
    """调用本地文本模型生成仅引用候选动作编号的结构化训练草稿。"""

    def __init__(
        self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None
    ) -> None:
        self._base_url = settings.ollama_base_url.rstrip("/")
        self._model = settings.ollama_workout_model
        self._timeout = settings.ollama_workout_timeout_seconds
        self._transport = transport

    @property
    def name(self) -> str:
        return "ollama"

    async def generate(self, request: WorkoutDraftRequest) -> WorkoutDraftResponse:
        schema = WorkoutDraftResponse.model_json_schema(by_alias=True)
        payload: dict[str, Any] = {
            "model": self._model,
            "stream": False,
            "think": False,
            "format": schema,
            "options": {"temperature": 0.15},
            "messages": [{"role": "user", "content": self._prompt(request, schema)}],
        }
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                response = await client.post(
                    f"{self._base_url}/api/chat", json=payload, timeout=self._timeout
                )
        except httpx.TimeoutException as error:
            raise AgentServiceError(504, "WORKOUT_MODEL_TIMEOUT", "训练草稿生成超时") from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503, "WORKOUT_MODEL_UNAVAILABLE", "训练草稿模型暂时不可用"
            ) from error
        if response.status_code >= 400:
            raise AgentServiceError(502, "WORKOUT_MODEL_REQUEST_FAILED", "训练草稿模型调用失败")
        try:
            chat = OllamaChatResponse.model_validate(response.json())
        except ValueError as error:
            raise AgentServiceError(
                502,
                "WORKOUT_MODEL_RESPONSE_INVALID",
                "训练草稿模型响应结构无法校验",
                self._validation_details(error, "ollamaResponse"),
            ) from error
        try:
            draft = WorkoutDraftResponse.model_validate_json(chat.message.content)
        except ValueError as error:
            raise AgentServiceError(
                502,
                "WORKOUT_MODEL_DRAFT_INVALID",
                "训练草稿模型结果无法校验",
                self._validation_details(error, "draft"),
            ) from error
        return draft.model_copy(
            update={
                "request_id": request.request_id,
                "provider": self.name,
                "disclaimer": "训练草稿仅供健身参考，请根据现场状态调整，并在确认后才保存。",
            }
        )

    @staticmethod
    def _validation_details(
        error: ValueError, prefix: str
    ) -> list[dict[str, object]]:
        """提取可安全持久化的字段路径；不记录模型原文和 Pydantic input。"""

        if not isinstance(error, ValidationError):
            return [{"field": prefix, "type": error.__class__.__name__}]
        return [
            {
                "field": ".".join([prefix, *(str(part) for part in item["loc"])]),
                "type": item["type"],
                "message": item["msg"],
            }
            for item in error.errors(include_url=False, include_context=False, include_input=False)
        ]

    @staticmethod
    def _prompt(request: WorkoutDraftRequest, schema: dict[str, Any]) -> str:
        context = request.model_dump(by_alias=True, mode="json")
        return (
            "你是健身应用的训练计划生成器。只能使用 candidates 中的 exerciseCode，不能创造动作。"
            "userRequest 是用户本次训练的最高优先级需求，必须据此确定训练部位和时长；"
            "trainingIntent 是 Java 从本次需求提取的硬约束：每个 focusRequirements 都必须分别满足，"
            "excludedRegions 中的部位绝不能出现在草稿中，"
            "只能按 candidate.primaryRegion 计算主训练部位，不能把辅助参与肌群当成目标部位；"
            "整份草稿必须包含2到12个不重复动作；"
            "minimumDurationMinutes 和 maximumDurationMinutes 仅表示用户可用时间，"
            "用于控制动作数量和训练规模；不要为了凑分钟数虚增单组动作时长。"
            "preferences 是长期偏好与安全边界，本次需求不能覆盖身体限制或排除动作。"
            "动作必须符合用户器械、限制、目标RPE和单次时长；优先使用 preferredExerciseCodes，"
            "绝不能使用 excludedExerciseCodes。新生成动作统一给出4组，用户后续可以在草稿中修改；"
            "每个动作还要给出次数、动作时长、组间休息、RPE"
            "和简短理由。重量不能猜测：只有 candidate.recentWeightKg 存在时才可原样写入 weight；"
            "没有历史重量、BODYWEIGHT 或 NO_LOAD 动作都必须返回 null。ASSISTANCE 表示辅助配重，"
            "不是普通训练负重。若 replaceExerciseCode 存在，只能用相同 primaryRegion 的新动作"
            "替换该动作，"
            "并保持 currentExercises 其他动作、动作数量及顺序不变。若 currentExercises 非空且没有 "
            "replaceExerciseCode，"
            "说明用户正在纠正上一版，"
            "必须参考上一版动作并解决 userRequest 的补充意见。若 validationIssues 非空，这是 Java "
            "规则校验反馈，只允许修正一次。"
            "status 必须为 READY，provider 可暂填 ollama。只返回符合 JSON Schema 的 JSON。\n"
            f"上下文：{json.dumps(context, ensure_ascii=False)}\n"
            f"JSON Schema：{json.dumps(schema, ensure_ascii=False)}"
        )
