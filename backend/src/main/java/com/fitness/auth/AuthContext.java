package com.fitness.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {
    public static final String USER_ID_ATTRIBUTE = "userId";

    private AuthContext() {
    }

    public static Long getUserId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("当前请求缺少登录用户信息");
    }
}
