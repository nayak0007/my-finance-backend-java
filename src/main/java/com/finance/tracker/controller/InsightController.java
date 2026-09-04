package com.finance.tracker.controller;

import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.InsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {
    private final InsightsService insights;

    public InsightController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("data", insights.compute(AuthSupport.currentUserId()));
    }
}
