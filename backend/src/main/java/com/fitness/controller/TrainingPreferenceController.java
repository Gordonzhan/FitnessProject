package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.dto.TrainingPreferenceRequest;
import com.fitness.service.TrainingPreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/training-preferences")
public class TrainingPreferenceController {
    private final TrainingPreferenceService service;

    public TrainingPreferenceController(TrainingPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public Response get(HttpServletRequest request) {
        return Response.success(service.get(AuthContext.getUserId(request)));
    }

    @PostMapping
    public Response save(@Valid @RequestBody TrainingPreferenceRequest body, HttpServletRequest request) {
        return Response.success(service.save(AuthContext.getUserId(request), body));
    }
}
