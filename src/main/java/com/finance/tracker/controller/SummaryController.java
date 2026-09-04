package com.finance.tracker.controller;

import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.SummaryService;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {
    private final SummaryService summaries;

    public SummaryController(SummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/monthly")
    public List<Map<String, Object>> monthly(@RequestParam(defaultValue = "12") int months) {
        int capped = Math.min(Math.max(months, 1), 36);
        return summaries.monthly(AuthSupport.currentUserId(), capped);
    }

    @GetMapping("/spending-by-category")
    public Map<String, Object> spending(@RequestParam(required = false) String month) {
        String m = month == null || month.isBlank() ? YearMonth.now().toString() : month;
        return summaries.spendingByCategory(AuthSupport.currentUserId(), m);
    }
}
