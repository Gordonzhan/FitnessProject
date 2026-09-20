package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.dto.AnalysisRangeRequest;
import com.fitness.dto.WeightRecordRequest;
import com.fitness.service.AnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/summary")
    public Response summary(@Valid @RequestBody AnalysisRangeRequest params, HttpServletRequest request) {
        return Response.success(analysisService.analyze(
                AuthContext.getUserId(request), params.startDate(), params.endDate()));
    }

    @PostMapping("/weight")
    public Response recordWeight(@Valid @RequestBody WeightRecordRequest params, HttpServletRequest request) {
        return Response.success(analysisService.recordWeight(
                AuthContext.getUserId(request), params.date(), params.weight()));
    }
}
