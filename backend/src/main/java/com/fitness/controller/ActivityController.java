package com.fitness.controller;

import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.dto.WeRunSyncRequest;
import com.fitness.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activity/wechat")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/summary")
    public Response summary(HttpServletRequest request) {
        return Response.success(activityService.summary(AuthContext.getUserId(request)));
    }

    @PostMapping("/sync")
    public Response sync(@Valid @RequestBody WeRunSyncRequest params, HttpServletRequest request) {
        return Response.success(activityService.syncWechatSteps(
                AuthContext.getUserId(request), params,
                request.getHeader("X-WX-OPENID"), request.getHeader("X-WX-APPID")));
    }

    @DeleteMapping
    @AuditedOperation(AuditEvent.ACTIVITY_DATA_DELETE)
    public Response delete(HttpServletRequest request) {
        activityService.deleteWechatData(AuthContext.getUserId(request));
        return Response.success();
    }
}
