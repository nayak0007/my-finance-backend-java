package com.finance.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TxnDtos {
    private TxnDtos() {}

    public record CreateTxnRequest(
            @NotNull UUID accountId,
            @NotBlank @Size(max = 40) String categoryKey,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String note,
            @NotNull Integer amount,
            @NotNull Instant date,
            @Pattern(regexp = "sms|email|statement|investment|manual") String source,
            @DecimalMin("0") @DecimalMax("1") BigDecimal confidence
    ) {}

    public record UpdateTxnRequest(
            UUID accountId,
            String categoryKey,
            String title,
            String note,
            Integer amount,
            Instant date,
            @Pattern(regexp = "sms|email|statement|investment|manual") String source,
            BigDecimal confidence
    ) {}

    public record BulkTxnRequest(
            @NotNull @Size(min = 1, max = 500) List<@Valid CreateTxnRequest> transactions
    ) {}

    public record TxnResponse(
            UUID id,
            UUID userId,
            UUID accountId,
            String categoryKey,
            String title,
            String note,
            Integer amount,
            Instant date,
            String source,
            BigDecimal confidence,
            Instant createdAt
    ) {}
}
