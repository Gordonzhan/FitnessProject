package com.fitness.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.Response;
import com.fitness.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fitness.controller.RequestTraceFilter;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final AuthTokenService authTokenService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthTokenService authTokenService, UserService userService, ObjectMapper objectMapper) {
        this.authTokenService = authTokenService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        String token = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : null;
        Optional<Long> userId = authTokenService.parseUserId(token);

        if (userId.isEmpty() || userService.findById(userId.get()) == null) {
            Object requestId = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE);
            log.warn("AUTHENTICATION_REJECTED requestId={} method={} path={} reason=invalid_or_expired_token",
                    requestId == null ? "unavailable" : requestId,
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), Response.error(401, "登录已失效，请重新登录"));
            return false;
        }

        request.setAttribute(AuthContext.USER_ID_ATTRIBUTE, userId.get());
        return true;
    }
}
