package com.fitness.controller;

import com.fitness.common.Response;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/database/food/search");

    @Test
    void databaseErrorsUse503AndDoNotLeakSqlToClient() {
        request.setAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, "test-request-id");
        ResponseEntity<Response> result = handler.handleDataAccessException(
                new DataRetrievalFailureException("select secret from internal_table"), request);

        assertEquals(503, result.getStatusCode().value());
        assertEquals(503, result.getBody().getCode());
        assertEquals("基础数据暂时无法访问，请稍后重试", result.getBody().getMessage());
        assertFalse(result.getBody().getMessage().contains("select"));
    }

    @Test
    void unexpectedErrorsDoNotLeakImplementationDetails() {
        ResponseEntity<Response> result = handler.handleException(
                new RuntimeException("internal path and password"), request);

        assertEquals(500, result.getStatusCode().value());
        assertEquals(500, result.getBody().getCode());
        assertEquals("服务器处理失败，请稍后重试", result.getBody().getMessage());
    }
}
