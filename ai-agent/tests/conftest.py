from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app


@pytest.fixture
def client() -> Iterator[TestClient]:
    # 固定 Provider 和服务身份，避免开发机 .env 让单元测试误连真实服务或意外开启认证。
    app = create_app(
        Settings(
            environment="test",
            provider="fake",
            recipe_provider="fake",
            knowledge_enabled=False,
            service_token="",
            java_service_token="",
        )
    )
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def recognition_payload() -> dict[str, object]:
    return {
        "requestId": "test-request-001",
        "imageUrls": ["https://example.com/meal.jpg"],
        "userContext": {
            "goal": "FAT_LOSS",
            "dailyCalorieTarget": 1800,
            "consumedCalories": 920,
        },
        "mode": "DRAFT_ONLY",
    }
