package com.finance.tracker.controller;

import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.RecurringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/recurring")
public class RecurringController {
    private final RecurringService recurring;

    public RecurringController(RecurringService recurring) {
        this.recurring = recurring;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("data", recurring.list(AuthSupport.currentUserId()));
    }
}
