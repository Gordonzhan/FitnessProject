package com.fitness.controller;

import com.fitness.ai.AgentUserContextService;
import com.fitness.common.Response;
import com.fitness.dto.FitnessAgentDtos.SearchRequest;
import com.fitness.service.AgentReadToolService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 仅供 Python Agent 使用的最小只读网关；不接受小程序用户 Token 或 userId 参数。 */
@RestController
@RequestMapping("/internal/ai")
public class InternalAiController {
    private final AgentUserContextService contexts;
    private final AgentReadToolService tools;
    private final String serviceToken;

    public InternalAiController(AgentUserContextService contexts, AgentReadToolService tools,
                                @Value("${fitness.agent.service-token:}") String serviceToken) {
        this.contexts = contexts; this.tools = tools; this.serviceToken = serviceToken;
    }

    @GetMapping("/context/profile") public Response profile(HttpServletRequest request) {
        return Response.success(tools.profile(userId(request)));
    }
    @GetMapping("/context/preferences") public Response preferences(HttpServletRequest request) {
        return Response.success(tools.preferences(userId(request)));
    }
    @GetMapping("/context/analysis") public Response analysis(@RequestParam(defaultValue="7") int days,
                                                               HttpServletRequest request) {
        validateDays(days); return Response.success(tools.recentAnalysis(userId(request), days));
    }
    @GetMapping("/context/nutrition/today") public Response nutrition(HttpServletRequest request) {
        return Response.success(tools.todayNutrition(userId(request)));
    }
    @GetMapping("/context/workouts/recent") public Response workouts(@RequestParam(defaultValue="7") int days,
                                                                     HttpServletRequest request) {
        validateDays(days); return Response.success(tools.recentWorkouts(userId(request), days));
    }
    @PostMapping("/tools/foods/search") public Response foods(@Valid @RequestBody SearchRequest body,
                                                               HttpServletRequest request) {
        userId(request); return Response.success(tools.searchFoods(body.keyword(), body.limit()));
    }
    @PostMapping("/tools/exercises/search") public Response exercises(@Valid @RequestBody SearchRequest body,
                                                                       HttpServletRequest request) {
        userId(request); return Response.success(tools.searchExercises(body.keyword(), body.limit()));
    }
    /** MCP Demo 只读取公共动作目录，不要求也不接受用户上下文。 */
    @PostMapping("/public/tools/exercises/search")
    public Response publicExercises(@Valid @RequestBody SearchRequest body,
                                    HttpServletRequest request) {
        authenticate(request);
        return Response.success(tools.searchExercises(body.keyword(), body.limit()));
    }

    private Long userId(HttpServletRequest request) {
        authenticate(request);
        return contexts.verify(request.getHeader("X-Agent-User-Context"));
    }
    private void authenticate(HttpServletRequest request) {
        if (!StringUtils.hasText(serviceToken)) return;
        String expected = "Bearer " + serviceToken;
        String actual = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) throw new SecurityException("Agent内部服务认证失败");
    }
    private void validateDays(int days) {
        if (days != 7 && days != 30) throw new IllegalArgumentException("查询周期只支持7天或30天");
    }
}
