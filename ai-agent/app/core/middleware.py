from __future__ import annotations

import time
from contextvars import ContextVar
from uuid import uuid4

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint

current_request_id: ContextVar[str] = ContextVar("request_id", default="-")


class RequestContextMiddleware(BaseHTTPMiddleware):
    """维护请求链路 ID，并记录每个 HTTP 请求的状态和耗时。"""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        """优先沿用 Java 传入的 ID，否则生成新 ID 并回写响应头。"""

        request_id = request.headers.get("X-Request-Id") or str(uuid4())
        # ContextVar 保证并发请求的日志不会串用其他请求的 requestId。
        token = current_request_id.set(request_id)
        started_at = time.perf_counter()
        try:
            response = await call_next(request)
            response.headers["X-Request-Id"] = request_id

            # 延迟导入可避免 logging 模块与 middleware 模块形成循环导入。
            from app.core.logging import logger

            logger.info(
                "http_request_completed",
                extra={
                    "method": request.method,
                    "path": request.url.path,
                    "statusCode": response.status_code,
                    "durationMs": round((time.perf_counter() - started_at) * 1000, 2),
                },
            )
            return response
        finally:
            current_request_id.reset(token)
