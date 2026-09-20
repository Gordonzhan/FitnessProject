package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.auth.AuthTokenService;
import com.fitness.auth.WechatAccountService;
import com.fitness.auth.WechatLoginService;
import com.fitness.common.Response;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.UserLoginRequest;
import com.fitness.dto.UserUpdateRequest;
import com.fitness.entity.User;
import com.fitness.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    private final WechatLoginService wechatLoginService;
    private final WechatAccountService wechatAccountService;
    private final AuthTokenService authTokenService;

    public UserController(UserService userService,
                          WechatLoginService wechatLoginService,
                          WechatAccountService wechatAccountService,
                          AuthTokenService authTokenService) {
        this.userService = userService;
        this.wechatLoginService = wechatLoginService;
        this.wechatAccountService = wechatAccountService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/login")
    public Response login(@Valid @RequestBody UserLoginRequest params, HttpServletRequest request) {
        String openid = wechatLoginService.resolveLoginOpenid(
                params.code(),
                request.getHeader("X-WX-OPENID"),
                request.getHeader("X-WX-APPID"));
        User user = wechatAccountService.getOrCreate(openid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", authTokenService.createToken(user.getId()));
        result.put("user", toUserView(user));
        return Response.success(result);
    }

    @GetMapping("/me")
    public Response getCurrentUser(HttpServletRequest request) {
        User user = userService.findById(AuthContext.getUserId(request));
        if (user == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "用户不存在");
        }
        return Response.success(toUserView(user));
    }

    @PostMapping("/update")
    public Response updateUser(@Valid @RequestBody UserUpdateRequest params, HttpServletRequest request) {
        Long currentUserId = AuthContext.getUserId(request);
        User existingUser = userService.findById(currentUserId);
        if (existingUser == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "用户不存在");
        }

        User user = new User();
        user.setId(currentUserId);
        user.setOpenid(existingUser.getOpenid());
        user.setDisplayName(normalizeDisplayName(params.displayName()));
        user.setGender(params.gender());
        user.setAge(params.age());
        user.setHeight(params.height());
        user.setWeight(params.weight());
        user.setGoal(params.goal());
        user.setActivityLevel(params.activityLevel());
        User updatedUser = userService.updateUser(user, params.weightRecordDate());
        return Response.success(toUserView(updatedUser));
    }

    @PostMapping("/get-daily-calories")
    public Response getDailyCalories(HttpServletRequest request) {
        User user = userService.findById(AuthContext.getUserId(request));
        if (user == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "用户不存在");
        }
        return Response.success(userService.calculateDailyCalories(user));
    }

    private Map<String, Object> toUserView(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("displayName", user.getDisplayName());
        result.put("gender", user.getGender());
        result.put("age", user.getAge());
        result.put("height", user.getHeight());
        result.put("weight", user.getWeight());
        result.put("goal", user.getGoal());
        result.put("activityLevel", user.getActivityLevel());
        return result;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        return displayName.trim();
    }
}
