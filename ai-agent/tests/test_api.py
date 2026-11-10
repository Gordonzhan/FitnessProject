from __future__ import annotations

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.core.config import Settings
from app.main import create_app


def test_health_reports_active_provider(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "fitness-ai-agent",
        "version": "0.1.0",
        "provider": "fake",
    }
    assert response.headers["X-Request-Id"]


def test_fake_food_recognition_returns_typed_draft(
    client: TestClient, recognition_payload: dict[str, object]
) -> None:
    response = client.post(
        "/v1/vision/food-recognition",
        json=recognition_payload,
        headers={"X-Request-Id": "test-request-001"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "test-request-001"
    assert body["status"] == "SUCCEEDED"
    assert body["provider"] == "fake"
    assert body["foods"][0]["name"] == "鸡胸肉"
    assert body["foods"][0]["nutrition"]["proteinGrams"] == 46.5
    assert body["needsUserConfirmation"] is True
    assert body["recipeGenerationStatus"] == "SUCCEEDED"
    assert body["recipeSuggestion"]["name"] == "香煎鸡胸肉"
    assert len(body["recipeSuggestion"]["steps"]) == 2
    assert response.headers["X-Request-Id"] == "test-request-001"


def test_food_recognition_rejects_empty_image_list(
    client: TestClient, recognition_payload: dict[str, object]
) -> None:
    recognition_payload["imageUrls"] = []

    response = client.post("/v1/vision/food-recognition", json=recognition_payload)

    assert response.status_code == 422
    assert response.json()["code"] == "AGENT_REQUEST_INVALID"


def test_service_token_protects_business_endpoint(recognition_payload: dict[str, object]) -> None:
    app = create_app(
        Settings(
            environment="test",
            provider="fake",
            recipe_provider="fake",
            knowledge_enabled=False,
            service_token="test-secret",
        )
    )
    with TestClient(app) as secured_client:
        missing = secured_client.post("/v1/vision/food-recognition", json=recognition_payload)
        accepted = secured_client.post(
            "/v1/vision/food-recognition",
            json=recognition_payload,
            headers={"Authorization": "Bearer test-secret"},
        )

    assert missing.status_code == 401
    assert missing.json()["code"] == "AGENT_AUTH_REQUIRED"
    assert accepted.status_code == 200


def test_production_rejects_missing_internal_service_identity() -> None:
    with pytest.raises(ValidationError, match="service token must be at least 32 bytes"):
        Settings(environment="production", service_token="", java_service_token="")


def test_workout_draft_endpoint_uses_only_supplied_candidates(client: TestClient) -> None:
    response = client.post(
        "/v1/workouts/draft",
        json={
            "requestId": "workout-request-001",
            "date": "2026-10-08",
            "userRequest": "今天练胸，30分钟",
            "trainingIntent": {
                "minimumDurationMinutes": None,
                "maximumDurationMinutes": 30,
                "focusRequirements": [
                    {
                        "region": "CHEST",
                        "label": "胸部",
                        "minimumExercises": 1,
                        "maximumExercises": None,
                    }
                ],
            },
            "preferences": {
                "experienceLevel": "BEGINNER",
                "sessionMinutes": 30,
                "trainingLocation": "HOME",
                "trainingGoal": "GENERAL_FITNESS",
                "equipmentTags": ["无", "哑铃"],
                "limitationTags": [],
                "preferredExerciseCodes": ["EX_SQUAT"],
                "excludedExerciseCodes": [],
                "targetRpeMin": 5,
                "targetRpeMax": 7,
            },
            "candidates": [
                {
                    "exerciseCode": "EX_SQUAT",
                    "name": "深蹲",
                    "category": "力量",
                    "primaryMuscles": "腿部",
                    "primaryRegion": "LEGS",
                    "equipment": "无",
                    "intensity": "中",
                    "defaultSets": 3,
                    "defaultReps": 12,
                    "defaultDurationSeconds": 40,
                    "defaultRestSeconds": 60,
                    "defaultRpe": 6,
                    "loadType": "BODYWEIGHT",
                    "recentWeightKg": None,
                },
                {
                    "exerciseCode": "EX_PUSH_UP",
                    "name": "俯卧撑",
                    "category": "力量",
                    "primaryMuscles": "胸部",
                    "primaryRegion": "CHEST",
                    "equipment": "无",
                    "intensity": "中",
                    "defaultSets": 3,
                    "defaultReps": 10,
                    "defaultDurationSeconds": 40,
                    "defaultRestSeconds": 60,
                    "defaultRpe": 6,
                    "loadType": "BODYWEIGHT",
                    "recentWeightKg": None,
                },
                {
                    "exerciseCode": "EX_DUMBBELL_PRESS",
                    "name": "哑铃卧推",
                    "category": "力量",
                    "primaryMuscles": "胸部,肱三头肌",
                    "primaryRegion": "CHEST",
                    "equipment": "哑铃",
                    "intensity": "中",
                    "defaultSets": 3,
                    "defaultReps": 10,
                    "defaultDurationSeconds": 40,
                    "defaultRestSeconds": 60,
                    "defaultRpe": 6,
                    "loadType": "BODYWEIGHT",
                    "recentWeightKg": None,
                },
            ],
            "mode": "DRAFT_ONLY",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "workout-request-001"
    assert body["status"] == "READY"
    assert {item["exerciseCode"] for item in body["exercises"]} == {
        "EX_PUSH_UP",
        "EX_DUMBBELL_PRESS",
    }
    assert all(5 <= item["rpe"] <= 7 for item in body["exercises"])
    assert body["title"] == "胸部专项训练"
