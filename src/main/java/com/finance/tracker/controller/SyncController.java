package com.finance.tracker.controller;

import com.finance.tracker.dto.SyncDtos;
import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService sync;

    public SyncController(SyncService sync) {
        this.sync = sync;
    }

    /** Per-user connection status (Gmail / SMS). */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return sync.status(AuthSupport.currentUserId());
    }

    /** Starts the server-side Google OAuth flow; returns the consent URL to open in a browser. */
    @PostMapping("/gmail/auth-url")
    public Map<String, Object> gmailAuthUrl() {
        return sync.startGmailAuth(AuthSupport.currentUserId());
    }

    /**
     * Google redirect target. Public (no auth header — it is a browser page).
     * Exchanges the code and stores the refresh token, then shows a closeable
     * confirmation page.
     */
    @GetMapping(value = "/gmail/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String gmailCallback(@RequestParam(required = false) String state,
                                @RequestParam(required = false) String code,
                                @RequestParam(required = false) String error) {
        return sync.completeGmailAuth(state, code, error);
    }

    /**
     * Runs one Gmail sync: fetches recent messages, parses alerts, de-dupes,
     * and returns the transactions. When {@code save: true} (and optionally an
     * {@code account_id}) they are inserted directly; otherwise the app previews
     * and saves via POST /transactions/bulk.
     */
    @PostMapping("/gmail/sync")
    public Map<String, Object> gmailSync(@Valid @RequestBody SyncDtos.GmailSyncRequest req) {
        return sync.gmailSync(AuthSupport.currentUserId(), req.accountId(),
                Boolean.TRUE.equals(req.save()), req.q(), req.maxResults() == null ? 25 : req.maxResults());
    }

    /** Disconnects Gmail and revokes the OAuth grant. */
    @DeleteMapping("/gmail")
    public Map<String, Object> gmailDisconnect() {
        sync.disconnectGmail(AuthSupport.currentUserId());
        return Map.of("ok", true);
    }

    /**
     * Parses raw SMS messages read on-device into classified, de-duplicated
     * transactions (never saves — the app previews, then bulk-imports).
     */
    @PostMapping("/sms/parse")
    public Map<String, Object> smsParse(@Valid @RequestBody SyncDtos.SmsParseRequest req) {
        return sync.smsParse(AuthSupport.currentUserId(), req.messages().stream()
                .map(m -> new SyncService.SmsMessage(m.id(), m.address(), m.body(), m.date()))
                .toList());
    }
}