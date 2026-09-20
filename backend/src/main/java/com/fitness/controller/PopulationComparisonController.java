package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.service.PopulationComparisonService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comparison")
public class PopulationComparisonController {
    private final PopulationComparisonService comparisonService;

    public PopulationComparisonController(PopulationComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/training")
    public Response training(HttpServletRequest request) {
        return Response.success(comparisonService.compareTraining(AuthContext.getUserId(request)));
    }
}
