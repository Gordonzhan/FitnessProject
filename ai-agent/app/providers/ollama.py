from __future__ import annotations

import base64
import json
from ipaddress import ip_address
from typing import Any
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, Field

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.providers.base import FoodRecognitionProvider, RecipeGenerationProvider
from app.schemas.base import ApiModel
from app.schemas.food import (
    FoodEstimate,
    FoodRecognitionRequest,
    FoodRecognitionResponse,
    RecipeStep,
    RecipeSuggestion,
    RecognitionStatus,
)


class OllamaFoodAnalysis(ApiModel):
    """模型负责生成的字段；请求标识、状态和 Provider 名称由服务端补充。"""

    foods: list[FoodEstimate] = Field(min_length=1, max_length=20)
    overall_confidence: float = Field(ge=0, le=1)
    needs_user_confirmation: bool = True
    warnings: list[str] = Field(default_factory=list, max_length=20)


class OllamaRecipeDraft(ApiModel):
    """文本模型可生成的字段；Provider 和免责声明由服务端固定补充。"""

    name: str = Field(min_length=1, max_length=100)
    steps: list[RecipeStep] = Field(min_length=1, max_length=12)
    confidence: float = Field(ge=0, le=1)


class OllamaMessage(BaseModel):
    """只解析 Ollama message 中当前业务需要的 content 字段。"""

    model_config = ConfigDict(extra="ignore")

    content: str


class OllamaChatResponse(BaseModel):
    """Ollama `/api/chat` 非流式响应的最小结构。"""

    model_config = ConfigDict(extra="ignore")

    message: OllamaMessage


class OllamaFoodRecognitionProvider(FoodRecognitionProvider):
    """通过 Ollama 原生 API 调用本地视觉模型完成食物识别。"""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        """保存模型配置；transport 参数用于测试时拦截 HTTP 请求。"""

        self._base_url = settings.ollama_base_url.rstrip("/")
        self._model = settings.ollama_model
        self._image_timeout = settings.request_timeout_seconds
        self._model_timeout = settings.ollama_timeout_seconds
        self._max_image_bytes = settings.max_image_bytes
        self._allow_private_image_urls = settings.allow_private_image_urls
        self._transport = transport

    @property
    def name(self) -> str:
        return "ollama"

    async def recognize(self, request: FoodRecognitionRequest) -> FoodRecognitionResponse:
        """下载请求图片、调用视觉模型并组装稳定的业务响应。"""

        try:
            async with httpx.AsyncClient(
                transport=self._transport,
                follow_redirects=False,
            ) as client:
                images = [
                    await self._download_image(client, str(image_url))
                    for image_url in request.image_urls
                ]
                analysis = await self._analyze(client, request, images)
        except AgentServiceError:
            raise
        except httpx.TimeoutException as error:
            raise AgentServiceError(
                504,
                "OLLAMA_TIMEOUT",
                "本地视觉模型响应超时，请稍后重试",
            ) from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503,
                "OLLAMA_UNAVAILABLE",
                "无法连接本地 Ollama 服务，请确认 Ollama 已启动",
            ) from error

        # 模型结果只能作为草稿，最终重量和营养值必须由用户确认或业务数据库重算。
        warnings = list(analysis.warnings)
        estimate_warning = "营养和重量为视觉模型估算值，保存前请由用户确认"
        if estimate_warning not in warnings:
            warnings.append(estimate_warning)

        return FoodRecognitionResponse(
            request_id=request.request_id,
            status=RecognitionStatus.SUCCEEDED,
            provider=self.name,
            foods=analysis.foods,
            overall_confidence=analysis.overall_confidence,
            needs_user_confirmation=True,
            warnings=warnings,
        )

    async def _download_image(self, client: httpx.AsyncClient, image_url: str) -> str:
        """安全下载图片并转换为 Ollama REST API 所需的 Base64。"""

        self._validate_image_url(image_url)
        try:
            async with client.stream(
                "GET",
                image_url,
                timeout=self._image_timeout,
                headers={"Accept": "image/*"},
            ) as response:
                # 不跟随重定向，防止公网地址跳转到本机或云环境内网资源。
                if response.is_redirect:
                    raise AgentServiceError(
                        400,
                        "IMAGE_REDIRECT_NOT_ALLOWED",
                        "图片地址不能发生重定向",
                    )
                if response.status_code >= 400:
                    raise AgentServiceError(
                        422,
                        "IMAGE_DOWNLOAD_FAILED",
                        "无法下载待识别图片",
                    )

                content_type = response.headers.get("content-type", "").split(";", 1)[0]
                if not content_type.startswith("image/"):
                    raise AgentServiceError(
                        422,
                        "IMAGE_CONTENT_TYPE_INVALID",
                        "图片地址返回的不是图片内容",
                    )

                declared_size = response.headers.get("content-length")
                if declared_size and int(declared_size) > self._max_image_bytes:
                    raise self._image_too_large_error()

                # 流式计数可防止服务端伪造 Content-Length 后返回超大文件。
                chunks: list[bytes] = []
                total_size = 0
                async for chunk in response.aiter_bytes():
                    total_size += len(chunk)
                    if total_size > self._max_image_bytes:
                        raise self._image_too_large_error()
                    chunks.append(chunk)
        except ValueError as error:
            raise AgentServiceError(
                422,
                "IMAGE_METADATA_INVALID",
                "图片响应元数据不合法",
            ) from error

        return base64.b64encode(b"".join(chunks)).decode("ascii")

    async def _analyze(
        self,
        client: httpx.AsyncClient,
        request: FoodRecognitionRequest,
        images: list[str],
    ) -> OllamaFoodAnalysis:
        """调用 Ollama `/api/chat` 并严格校验模型生成的 JSON。"""

        # JSON Schema 同时用于约束模型生成和 Pydantic 二次校验，不能信任未校验的模型文本。
        schema = OllamaFoodAnalysis.model_json_schema(by_alias=True)
        payload: dict[str, Any] = {
            "model": self._model,
            "stream": False,
            "think": False,
            "format": schema,
            "options": {"temperature": 0},
            "messages": [
                {
                    "role": "user",
                    "content": self._build_prompt(request, schema),
                    "images": images,
                }
            ],
        }

        response = await client.post(
            f"{self._base_url}/api/chat",
            json=payload,
            timeout=self._model_timeout,
        )
        if response.status_code >= 400:
            raise AgentServiceError(
                502,
                "OLLAMA_REQUEST_FAILED",
                "Ollama 模型调用失败，请检查模型名称和服务日志",
            )

        try:
            chat_response = OllamaChatResponse.model_validate(response.json())
            return OllamaFoodAnalysis.model_validate_json(chat_response.message.content)
        except ValueError as error:
            raise AgentServiceError(
                502,
                "OLLAMA_RESPONSE_INVALID",
                "视觉模型返回了无法校验的识别结果",
            ) from error

    @staticmethod
    def _build_prompt(request: FoodRecognitionRequest, schema: dict[str, Any]) -> str:
        """组合视觉任务指令、最小用户上下文和目标 JSON Schema。"""

        context = request.user_context.model_dump(by_alias=True, mode="json")
        return (
            "你是健身饮食记录应用的食物视觉识别器。分析所附图片；多张图片可能是同一餐的不同角度，"
            "不要重复计算。分别列出可见食物，保守估算可食重量范围、烹饪方式、热量、蛋白质、"
            "碳水和脂肪。无法从图片确认的配料或用油必须降低置信度并写入 warnings。"
            "用户提示只能作为辅助，不能覆盖图片证据。所有数值必须是非负数；置信度范围为 0 到 1。"
            "只返回符合 JSON Schema 的 JSON，不要 Markdown 或解释文本。\n"
            f"菜品提示：{request.dish_hint or '无'}\n"
            f"用户上下文：{json.dumps(context, ensure_ascii=False)}\n"
            f"JSON Schema：{json.dumps(schema, ensure_ascii=False)}"
        )

    def _validate_image_url(self, image_url: str) -> None:
        """拒绝非法协议以及默认禁止的本机、内网图片地址。"""

        # Java 会校验 HTTPS；此处仍执行独立校验，避免 Python 服务被绕过 Java 后直接滥用。
        parsed = urlsplit(image_url)
        hostname = parsed.hostname
        if parsed.scheme not in {"http", "https"} or not hostname:
            raise AgentServiceError(422, "IMAGE_URL_INVALID", "图片地址不合法")
        if not self._allow_private_image_urls and hostname.lower() == "localhost":
            raise AgentServiceError(422, "IMAGE_URL_NOT_ALLOWED", "图片地址不能指向本机")
        try:
            address = ip_address(hostname)
        except ValueError:
            return
        if not self._allow_private_image_urls and not address.is_global:
            raise AgentServiceError(422, "IMAGE_URL_NOT_ALLOWED", "图片地址不能指向内网")

    def _image_too_large_error(self) -> AgentServiceError:
        """构造包含当前大小限制的统一图片错误。"""

        size_mb = self._max_image_bytes // (1024 * 1024)
        return AgentServiceError(
            413,
            "IMAGE_TOO_LARGE",
            f"单张图片不能超过 {size_mb} MB",
        )


class OllamaRecipeGenerationProvider(RecipeGenerationProvider):
    """调用独立的 Ollama 文本模型，把视觉结果扩充为可编辑菜谱草稿。"""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = settings.ollama_base_url.rstrip("/")
        self._model = settings.ollama_recipe_model
        self._timeout = settings.ollama_recipe_timeout_seconds
        self._transport = transport

    @property
    def name(self) -> str:
        return "ollama"

    async def generate(
        self, request: FoodRecognitionRequest, foods: list[FoodEstimate]
    ) -> RecipeSuggestion:
        """只使用已校验的食材结果生成文案，不生成或覆盖营养数据。"""

        schema = OllamaRecipeDraft.model_json_schema(by_alias=True)
        food_context = [food.model_dump(by_alias=True, mode="json") for food in foods]
        payload: dict[str, Any] = {
            "model": self._model,
            "stream": False,
            "think": False,
            "format": schema,
            "options": {"temperature": 0.2},
            "messages": [
                {"role": "user", "content": self._build_prompt(request, food_context, schema)}
            ],
        }
        try:
            async with httpx.AsyncClient(transport=self._transport) as client:
                response = await client.post(
                    f"{self._base_url}/api/chat", json=payload, timeout=self._timeout
                )
        except httpx.TimeoutException as error:
            raise AgentServiceError(504, "RECIPE_MODEL_TIMEOUT", "菜谱建议生成超时") from error
        except httpx.RequestError as error:
            raise AgentServiceError(
                503, "RECIPE_MODEL_UNAVAILABLE", "菜谱模型暂时不可用"
            ) from error
        if response.status_code >= 400:
            raise AgentServiceError(502, "RECIPE_MODEL_REQUEST_FAILED", "菜谱模型调用失败")
        try:
            chat_response = OllamaChatResponse.model_validate(response.json())
            draft = OllamaRecipeDraft.model_validate_json(chat_response.message.content)
        except ValueError as error:
            raise AgentServiceError(
                502, "RECIPE_MODEL_RESPONSE_INVALID", "菜谱模型结果无法校验"
            ) from error
        return RecipeSuggestion(
            name=draft.name,
            steps=draft.steps,
            confidence=draft.confidence,
            provider=self.name,
            disclaimer="制作步骤由 AI 根据图片识别结果推测，请按实际情况修改。",
        )

    @staticmethod
    def _build_prompt(
        request: FoodRecognitionRequest,
        foods: list[dict[str, Any]],
        schema: dict[str, Any],
    ) -> str:
        """限制模型只写菜谱文案，并明确成品图无法证明真实制作过程。"""

        restrictions = request.user_context.dietary_restrictions
        return (
            "你是中文健身饮食应用的菜谱编辑。根据视觉模型已经识别的食材，生成一个简洁菜名和"
            "3至8个可执行步骤。这只是合理推测，不得声称还原了图片中的真实配方。"
            "不要生成营养数值，不要添加与已识别食材明显冲突的主要食材；盐、水和少量食用油等"
            "常见辅料应使用保守措辞。必须遵守饮食限制。只返回符合 JSON Schema 的 JSON。\n"
            f"用户菜品提示：{request.dish_hint or '无'}\n"
            f"饮食限制：{json.dumps(restrictions, ensure_ascii=False)}\n"
            f"视觉识别结果：{json.dumps(foods, ensure_ascii=False)}\n"
            f"JSON Schema：{json.dumps(schema, ensure_ascii=False)}"
        )
