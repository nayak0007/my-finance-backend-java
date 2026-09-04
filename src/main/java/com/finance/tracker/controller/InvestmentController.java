package com.finance.tracker.controller;

import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.InvestmentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentController {
    private final InvestmentService investments;

    public InvestmentController(InvestmentService investments) {
        this.investments = investments;
    }

    @GetMapping("/holdings")
    public Map<String, Object> holdings() {
        return Map.of("data", investments.holdings(AuthSupport.currentUserId()));
    }

    @GetMapping("/history")
    public Map<String, Object> history() {
        return Map.of("data", investments.history(AuthSupport.currentUserId()));
    }

    @GetMapping("/growth")
    public Map<String, Object> growth(@RequestParam(defaultValue = "12") int months) {
        int capped = Math.min(Math.max(months, 1), 36);
        return Map.of("data", investments.growth(AuthSupport.currentUserId(), capped));
    }
}
