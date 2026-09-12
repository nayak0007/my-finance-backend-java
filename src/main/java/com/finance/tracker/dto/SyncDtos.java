package com.finance.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class SyncDtos {
    private SyncDtos() {}

    /** One raw SMS message read on-device. */
    public record SmsMessage(
            @Size(max = 64) String id,
            @Size(max = 100) String address,
            @NotBlank @Size(max = 8000) String body,
            @Size(max = 64) String date
    ) {}

    public record SmsParseRequest(
            @NotNull @Size(min = 1, max = 2000) List<@Valid SmsMessage> messages
    ) {}

    public record GmailSyncRequest(
            UUID accountId,
            Boolean save,
            @Size(max = 200) String q,
            Integer maxResults
    ) {}
}