package com.fitness.controller;

import com.fitness.ai.AgentClient;
import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.dto.FoodRecognitionRequest;
import com.fitness.dto.WorkoutDraftConfirmRequest;
import com.fitness.dto.WorkoutDraftCreateRequest;
import com.fitness.dto.WorkoutDraftReplaceRequest;
import com.fitness.dto.WorkoutDraftRemoveRequest;
import com.fitness.service.FoodRecognitionDraftService;
import com.fitness.service.WorkoutDraftService;
import com.fitness.service.FitnessAgentConversationService;
import com.fitness.dto.FitnessAgentDtos.SendMessageRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

/** 小程序 AI 功能入口；客户端不直接连接 Python Agent。 */
@RestController
@RequestMapping("/ai")
public class AiController {
    private final AgentClient agentClient;
    private final FoodRecognitionDraftService draftService;
    private final WorkoutDraftService workoutDraftService;
    private final FitnessAgentConversationService conversationService;

    public AiController(AgentClient agentClient, FoodRecognitionDraftService draftService,
                        WorkoutDraftService workoutDraftService,
                        FitnessAgentConversationService conversationService) {
        this.agentClient = agentClient;
        this.draftService = draftService;
        this.workoutDraftService = workoutDraftService;
        this.conversationService = conversationService;
    }

    @GetMapping("/status")
    public Response status(HttpServletRequest request) {
        return Response.success(agentClient.status(requestId(request)));
    }

    @PostMapping("/food-recognition")
    public Response recognizeFood(@Valid @RequestBody FoodRecognitionRequest params,
                                  HttpServletRequest request) {
        return Response.success(agentClient.recognizeFood(params, requestId(request)));
    }

    /** 正式产品入口：校验图片归属，并将模型估算转换为数据库营养草稿。 */
    @PostMapping("/food-recognition/draft")
    public Response createFoodDraft(@Valid @RequestBody FoodRecognitionRequest params,
                                    HttpServletRequest request) {
        return Response.success(draftService.createDraft(
                AuthContext.getUserId(request), params, requestId(request)));
    }

    @PostMapping("/workout-draft")
    public Response createWorkoutDraft(@Valid @RequestBody WorkoutDraftCreateRequest params,
                                       HttpServletRequest request) {
        return Response.success(workoutDraftService.create(
                AuthContext.getUserId(request), params.date(), params.message(), params.currentDraft(),
                requestId(request)));
    }

    @PostMapping("/workout-draft/replace")
    public Response replaceWorkoutExercise(@Valid @RequestBody WorkoutDraftReplaceRequest params,
                                           HttpServletRequest request) {
        return Response.success(workoutDraftService.replace(AuthContext.getUserId(request),
                params.date(), params.draft(), params.message(), params.exerciseCode(), requestId(request)));
    }

    @PostMapping("/workout-draft/remove")
    public Response removeWorkoutExercise(@Valid @RequestBody WorkoutDraftRemoveRequest params,
                                          HttpServletRequest request) {
        return Response.success(workoutDraftService.remove(AuthContext.getUserId(request),
                params.date(), params.draft(), params.exerciseCode()));
    }

    @PostMapping("/workout-draft/confirm")
    public Response confirmWorkoutDraft(@Valid @RequestBody WorkoutDraftConfirmRequest params,
                                        HttpServletRequest request) {
        return Response.success(workoutDraftService.confirm(
                AuthContext.getUserId(request), params.date(), params.draft()));
    }

    @PostMapping("/agent/sessions")
    public Response openAgentSession(HttpServletRequest request) {
        return Response.success(conversationService.getOrCreate(AuthContext.getUserId(request)));
    }

    @GetMapping("/agent/sessions/{sessionId}")
    public Response getAgentSession(@PathVariable String sessionId, HttpServletRequest request) {
        return Response.success(conversationService.get(AuthContext.getUserId(request), sessionId));
    }

    @PostMapping("/agent/sessions/{sessionId}/messages")
    public Response sendAgentMessage(@PathVariable String sessionId,
                                     @Valid @RequestBody SendMessageRequest params,
                                     HttpServletRequest request) {
        return Response.success(conversationService.send(AuthContext.getUserId(request), sessionId,
                params.content(), requestId(request)));
    }

    @DeleteMapping("/agent/sessions/{sessionId}/messages")
    public Response clearAgentMessages(@PathVariable String sessionId, HttpServletRequest request) {
        return Response.success(conversationService.clear(AuthContext.getUserId(request), sessionId));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "missing-request-id" : value.toString();
    }
}
