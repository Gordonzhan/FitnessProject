from __future__ import annotations

import base64
import json

import httpx
import pytest

from app.api.errors import AgentServiceError
from app.core.config import Settings
from app.providers.factory import build_food_recognition_provider
from app.providers.ollama import OllamaFoodRecognitionProvider, OllamaRecipeGenerationProvider
from app.schemas.food import FoodEstimate, FoodRecognitionRequest, NutritionEstimate


def ollama_analysis_json() -> str:
    return json.dumps(
        {
            "foods": [
                {
                    "name": "烤鸡胸肉",
                    "estimatedWeightGrams": 145,
                    "weightRangeGrams": [120, 170],
                    "cookingMethod": "烤制",
                    "nutrition": {
                        "caloriesKcal": 239,
                        "proteinGrams": 44.9,
                        "carbohydrateGrams": 0,
                        "fatGrams": 5.2,
                    },
                    "confidence": 0.84,
                }
            ],
            "overallConfidence": 0.84,
            "needsUserConfirmation": True,
            "warnings": ["无法确认实际用油量"],
        },
        ensure_ascii=False,
    )


def recognition_request() -> FoodRecognitionRequest:
    return FoodRecognitionRequest.model_validate(
        {
            "requestId": "ollama-test-001",
            "imageUrls": ["https://images.example.test/meal.jpg"],
            "userContext": {"goal": "FAT_LOSS"},
            "dishHint": "鸡胸肉",
        }
    )


def recognized_foods() -> list[FoodEstimate]:
    return [
        FoodEstimate(
            name="烤鸡胸肉",
            estimated_weight_grams=145,
            weight_range_grams=(120, 170),
            cooking_method="烤制",
            nutrition=NutritionEstimate(
                calories_kcal=239,
                protein_grams=44.9,
                carbohydrate_grams=0,
                fat_grams=5.2,
            ),
            confidence=0.84,
        )
    ]


@pytest.mark.asyncio
async def test_ollama_provider_downloads_image_and_validates_structured_output() -> None:
    captured_payload: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "images.example.test":
            return httpx.Response(200, headers={"Content-Type": "image/jpeg"}, content=b"jpeg")

        assert request.url == httpx.URL("http://ollama.test/api/chat")
        captured_payload.update(json.loads(request.content))
        return httpx.Response(
            200,
            json={"message": {"role": "assistant", "content": ollama_analysis_json()}},
        )

    settings = Settings(
        environment="test",
        provider="ollama",
        ollama_base_url="http://ollama.test",
        ollama_model="qwen3-vl:8b-instruct",
    )
    provider = OllamaFoodRecognitionProvider(settings, httpx.MockTransport(handler))

    result = await provider.recognize(recognition_request())

    assert result.provider == "ollama"
    assert result.foods[0].name == "烤鸡胸肉"
    assert result.needs_user_confirmation is True
    assert "视觉模型估算值" in result.warnings[-1]
    assert captured_payload["model"] == "qwen3-vl:8b-instruct"
    assert captured_payload["stream"] is False
    assert captured_payload["think"] is False
    messages = captured_payload["messages"]
    assert isinstance(messages, list)
    assert messages[0]["images"] == [base64.b64encode(b"jpeg").decode("ascii")]
    assert isinstance(captured_payload["format"], dict)


@pytest.mark.asyncio
async def test_ollama_provider_rejects_non_image_response() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"Content-Type": "text/html"}, text="not an image")

    provider = OllamaFoodRecognitionProvider(
        Settings(environment="test", provider="ollama"),
        httpx.MockTransport(handler),
    )

    with pytest.raises(AgentServiceError) as captured:
        await provider.recognize(recognition_request())

    assert captured.value.status_code == 422
    assert captured.value.code == "IMAGE_CONTENT_TYPE_INVALID"


def test_factory_builds_ollama_provider() -> None:
    provider = build_food_recognition_provider(Settings(environment="test", provider="ollama"))

    assert provider.name == "ollama"


@pytest.mark.asyncio
async def test_ollama_recipe_provider_uses_separate_text_model_without_images() -> None:
    captured_payload: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured_payload.update(json.loads(request.content))
        content = json.dumps(
            {
                "name": "香烤鸡胸肉",
                "steps": ["鸡胸肉擦干并调味。", "烤至熟透后静置切片。"],
                "confidence": 0.76,
            },
            ensure_ascii=False,
        )
        return httpx.Response(200, json={"message": {"content": content}})

    settings = Settings(
        environment="test",
        provider="ollama",
        ollama_base_url="http://ollama.test",
        ollama_recipe_model="qwen3:8b",
    )
    provider = OllamaRecipeGenerationProvider(settings, httpx.MockTransport(handler))

    suggestion = await provider.generate(recognition_request(), recognized_foods())

    assert suggestion.name == "香烤鸡胸肉"
    assert suggestion.provider == "ollama"
    assert captured_payload["model"] == "qwen3:8b"
    messages = captured_payload["messages"]
    assert isinstance(messages, list)
    assert "images" not in messages[0]
    assert "不要生成营养数值" in messages[0]["content"]
