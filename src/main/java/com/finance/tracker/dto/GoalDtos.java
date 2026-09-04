package com.finance.tracker.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class GoalDtos {
    private GoalDtos() {}

    public record CreateGoalRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Positive Integer target,
            @Min(0) Integer saved,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dueDate,
            String color
    ) {}

    public record UpdateGoalRequest(
            String name,
            Integer target,
            Integer saved,
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String dueDate,
            String color
    ) {}

    public record GoalResponse(
            UUID id,
            UUID userId,
            String name,
            Integer target,
            Integer saved,
            LocalDate dueDate,
            String color,
            Double progressPct
    ) {}
}
