package com.finance.tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {}

    public record CreateAccountRequest(
            @NotBlank @Size(max = 80) String bank,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(min = 2, max = 8) String mask,
            @NotBlank @Pattern(regexp = "Savings|Salary|FD|Wallet") String type,
            Integer balance,
            BigDecimal changePct,
            String color,
            Integer inflow,
            Integer outflow,
            @Pattern(regexp = "connected|attention") String status
    ) {}

    public record UpdateAccountRequest(
            String bank,
            String name,
            String mask,
            @Pattern(regexp = "Savings|Salary|FD|Wallet") String type,
            Integer balance,
            BigDecimal changePct,
            String color,
            Integer inflow,
            Integer outflow,
            @Pattern(regexp = "connected|attention") String status
    ) {}

    public record AccountResponse(
            UUID id,
            UUID userId,
            String bank,
            String name,
            String mask,
            String type,
            Integer balance,
            BigDecimal changePct,
            String color,
            Integer inflow,
            Integer outflow,
            String status,
            Instant lastSyncedAt,
            Instant createdAt
    ) {}
}
