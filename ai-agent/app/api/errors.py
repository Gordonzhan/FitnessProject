from __future__ import annotations

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.logging import logger
from app.core.middleware import current_request_id


class AgentServiceError(Exception):
    """可安全返回给调用方的业务异常，不暴露底层网络或模型错误细节。"""

    def __init__(
        self,
        status_code: int,
        code: str,
        message: str,
        diagnostic_details: list[dict[str, object]] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        # 只用于服务端日志，不进入 API 响应，避免泄露模型输出或内部实现。
        self.diagnostic_details = diagnostic_details or []


def error_body(code: str, message: str, details: Any | None = None) -> dict[str, Any]:
    """生成所有接口统一使用的错误响应结构。"""

    body: dict[str, Any] = {
        "code": code,
        "message": message,
        "requestId": current_request_id.get(),
    }
    if details is not None:
        body["details"] = details
    return body


def register_exception_handlers(application: FastAPI) -> None:
    """注册业务、参数校验和未知异常的统一处理器。"""

    @application.exception_handler(AgentServiceError)
    async def handle_agent_error(_: Request, error: AgentServiceError) -> JSONResponse:
        logger.warning(
            "agent_service_error",
            extra={"statusCode": error.status_code, "errorCode": error.code},
        )
        return JSONResponse(
            status_code=error.status_code,
            content=error_body(error.code, error.message),
        )

    @application.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, error: RequestValidationError) -> JSONResponse:
        details = [
            {
                "field": ".".join(str(part) for part in item["loc"]),
                "message": item["msg"],
                "type": item["type"],
            }
            for item in error.errors()
        ]
        return JSONResponse(
            status_code=422,
            content=error_body("AGENT_REQUEST_INVALID", "请求参数不合法", details),
        )

    @application.exception_handler(Exception)
    async def handle_unexpected_error(_: Request, error: Exception) -> JSONResponse:
        # 未知异常只写服务端日志，对客户端隐藏堆栈和内部实现。
        logger.exception("unhandled_agent_error", extra={"errorType": type(error).__name__})
        return JSONResponse(
            status_code=500,
            content=error_body("AGENT_INTERNAL_ERROR", "Agent 服务暂时不可用"),
        )
