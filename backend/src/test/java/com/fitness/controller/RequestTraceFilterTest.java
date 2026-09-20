package com.fitness.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTraceFilterTest {
    private final RequestTraceFilter filter = new RequestTraceFilter();

    @Test
    void preservesSafeRequestIdInAttributeAndResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.REQUEST_ID_HEADER, "client_request_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("client_request_123", request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE));
        assertEquals("client_request_123", response.getHeader(RequestTraceFilter.REQUEST_ID_HEADER));
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.REQUEST_ID_HEADER, "bad header value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(RequestTraceFilter.REQUEST_ID_HEADER);
        assertTrue(generated.matches("[0-9a-f-]{36}"));
    }
}
