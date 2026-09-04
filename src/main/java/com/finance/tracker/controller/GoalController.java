package com.finance.tracker.controller;

import com.finance.tracker.dto.GoalDtos;
import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {
    private final GoalService goals;

    public GoalController(GoalService goals) {
        this.goals = goals;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("data", goals.list(AuthSupport.currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody GoalDtos.CreateGoalRequest req) {
        return goals.create(AuthSupport.currentUserId(), req);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody GoalDtos.UpdateGoalRequest req) {
        return goals.update(AuthSupport.currentUserId(), id, req);
    }
}
