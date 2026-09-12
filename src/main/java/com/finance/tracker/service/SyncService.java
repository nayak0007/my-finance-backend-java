package com.finance.tracker.service;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.domain.Account;
import com.finance.tracker.domain.SyncConnection;
import com.finance.tracker.domain.Txn;
import com.finance.tracker.exception.AppException;
import com.finance.tracker.repository.AccountRepository;
import com.finance.tracker.repository.SyncConnectionRepository;
import com.finance.tracker.repository.TxnRepository;
import com.finance.tracker.security.TokenCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SMS + Gmail sync.
 *
 * Gmail: the user authorizes via a server-side Google OAuth flow (the app opens
 * the auth URL in a browser, Google redirects to the backend callback, the
 * refresh token is stored AES-encrypted). A sync run then pulls recent
 * messages, parses bank alerts, de-dupes against `transactions.external_id`
 * and either returns them for preview or saves them directly.
 *
 * SMS: the device reads SMS with a native module and POSTs the raw messages;
 * parsing/classification happens here so the rules stay in one place.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final String PROVIDER_GMAIL = "gmail";
    private static final String PROVIDER_SMS = "sms";
    private static final String DEFAULT_GMAIL_QUERY = "newer_than:30d";

    private final SyncConnectionRepository connections;
    private final TxnRepository txns;
    private final AccountRepository accounts;
    private final GmailService gmail;
    private final ImportService imports;
    private final TokenCrypto crypto;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public SyncService(SyncConnectionRepository connections, TxnRepository txns, AccountRepository accounts,
                       GmailService gmail, ImportService imports, TokenCrypto crypto, AppProperties props) {
        this.connections = connections;
        this.txns = txns;
        this.accounts = accounts;
        this.gmail = gmail;
        this.imports = imports;
        this.crypto = crypto;
        this.props = props;
    }

    /* ------------------------------------------------------------------ status */

    public Map<String, Object> status(UUID userId) {
        List<Map<String, Object>> list = connections.findByUserIdOrderByProvider(userId).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("provider", c.getProvider());
                    m.put("email", c.getEmail());
                    m.put("status", c.getStatus());
                    m.put("last_synced_at", c.getLastSyncedAt());
                    m.put("last_synced_count", c.getLastSyncedCount());
                    return m;
                })
                .toList();
        return Map.of("data", list);
    }

    /* ------------------------------------------------------------------ gmail */

    @Transactional
    public Map<String, Object> startGmailAuth(UUID userId) {
        String state = randomHex(32);
        SyncConnection conn = connections.findByUserIdAndProvider(userId, PROVIDER_GMAIL).orElseGet(() -> {
            SyncConnection c = new SyncConnection();
            c.setUserId(userId);
            c.setProvider(PROVIDER_GMAIL);
            return c;
        });
        conn.setStatus("pending");
        conn.setState(state);
        conn.setAccessTokenEncrypted(null);
        conn.setRefreshTokenEncrypted(null);
        conn.setTokenExpiresAt(null);
        conn.setLastError(null);
        conn.setUpdatedAt(Instant.now());
        connections.save(conn);
        String url = gmail.buildAuthUrl(state);
        log.info("sync gmail auth-url user={}", userId);
        return Map.of("url", url);
    }

    /** Browser callback: validates state, exchanges the code, stores tokens. Returns an HTML page. */
    @Transactional
    public String completeGmailAuth(String state, String code, String error) {
        if (state == null || state.isBlank()) {
            return htmlPage("Sync failed", "Missing OAuth state. Please try connecting Gmail again from the app.");
        }
        SyncConnection conn = connections.findByProviderAndState(PROVIDER_GMAIL, state)
                .orElseThrow(() -> AppException.badRequest("Invalid or expired OAuth state — start again from the app"));
        if (error != null && !error.isBlank()) {
            conn.setStatus("error");
            conn.setLastError(error);
            conn.setUpdatedAt(Instant.now());
            connections.save(conn);
            log.warn("sync gmail callback error user={} error={}", conn.getUserId(), error);
            return htmlPage("Gmail not connected", "Authorization was cancelled or denied. Close this window and try again.");
        }
        if (code == null || code.isBlank()) {
            throw AppException.badRequest("Missing OAuth authorization code");
        }
        try {
            GmailService.TokenResponse tokens = gmail.exchangeCode(code);
            if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
                throw AppException.upstream("Google did not return a refresh token (re-consent required)");
            }
            conn.setEmail(GmailService.emailFromIdToken(tokens.idToken()));
            conn.setRefreshTokenEncrypted(crypto.encrypt(tokens.refreshToken()));
            conn.setAccessTokenEncrypted(crypto.encrypt(tokens.accessToken()));
            conn.setTokenExpiresAt(tokens.expiresIn() == null ? null : Instant.now().plusSeconds(tokens.expiresIn() - 60));
            conn.setScope(props.getGmail().getScopes());
            conn.setStatus("connected");
            conn.setState(null);
            conn.setLastError(null);
            conn.setUpdatedAt(Instant.now());
            connections.save(conn);
            log.info("sync gmail connected user={}", conn.getUserId());
            return htmlPage("Gmail connected", "Your Gmail is now linked. You can close this window and return to the app.");
        } catch (Exception e) {
            conn.setStatus("error");
            conn.setLastError(e instanceof AppException ae ? ae.getMessage() : e.getMessage());
            conn.setUpdatedAt(Instant.now());
            connections.save(conn);
            log.warn("sync gmail callback failed user={} reason={}", conn.getUserId(), e.getMessage());
            return htmlPage("Gmail not connected", "Something went wrong while linking your Gmail. Close this window and try again.");
        }
    }

    @Transactional
    public void disconnectGmail(UUID userId) {
        SyncConnection conn = connections.findByUserIdAndProvider(userId, PROVIDER_GMAIL).orElse(null);
        if (conn == null) return;
        String refresh = crypto.decrypt(conn.getRefreshTokenEncrypted());
        if (refresh != null) {
            gmail.revokeToken(refresh);
        }
        connections.delete(conn);
        log.info("sync gmail disconnected user={}", userId);
    }

    /**
     * Fetches recent Gmail messages, parses bank alerts, de-dupes and (when
     * save=true) inserts them. Returns parsed transactions for preview.
     */
    @Transactional
    public Map<String, Object> gmailSync(UUID userId, UUID accountId, boolean save, String query, int maxResults) {
        SyncConnection conn = connections.findByUserIdAndProvider(userId, PROVIDER_GMAIL)
                .filter(c -> "connected".equals(c.getStatus()))
                .orElseThrow(() -> AppException.notFound("Gmail is not connected. Connect it from Smart Import first."));

        String accessToken = ensureAccessToken(conn);
        String q = (query == null || query.isBlank()) ? DEFAULT_GMAIL_QUERY : query;
        int capped = Math.min(Math.max(maxResults, 1), 100);

        List<String> ids;
        try {
            ids = gmail.listMessages(accessToken, q, capped);
        } catch (AppException e) {
            conn.setLastError(e.getMessage());
            conn.setUpdatedAt(Instant.now());
            connections.save(conn);
            throw e;
        }
        log.info("sync gmail fetch user={} messages={}", userId, ids.size());

        List<Map<String, Object>> parsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int failures = 0;
        for (String id : ids) {
            try {
                GmailService.GmailMessage msg = gmail.getMessage(accessToken, id);
                String text = join(msg.subject(), msg.body());
                String messageDate = msg.epochMillis() > 0 ? Instant.ofEpochMilli(msg.epochMillis()).toString() : null;
                AlertParser.AlertTxn alert = AlertParser.parse(msg.from(), text, messageDate).orElse(null);
                if (alert == null) continue;
                String externalId = "gmail:" + id;
                if (!seen.add(externalId)) continue;
                parsed.add(toTxnMap(alert, "email", externalId));
            } catch (AppException e) {
                failures++;
                if (failures > 10) {
                    log.warn("sync gmail aborting after {} failures user={}", failures, userId);
                    break;
                }
            }
        }

        Map<String, Object> out = finishParse(userId, parsed, accountId, save);
        conn.setLastSyncedAt(Instant.now());
        conn.setLastSyncedCount((Integer) out.get("saved") + (Integer) out.get("skipped"));
        conn.setLastError(null);
        conn.setUpdatedAt(Instant.now());
        connections.save(conn);
        log.info("sync gmail done user={} parsed={} skipped={} saved={}", userId, parsed.size(), out.get("skipped"), out.get("saved"));
        return out;
    }

    /* -------------------------------------------------------------------- sms */

    /**
     * Parses raw SMS messages read on-device, de-dupes and returns transactions
     * for preview (the app saves them through the bulk endpoint).
     */
    @Transactional
    public Map<String, Object> smsParse(UUID userId, List<SmsMessage> messages) {
        log.info("sync sms parse user={} messages={}", userId, messages.size());
        List<Map<String, Object>> parsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SmsMessage msg : messages) {
            AlertParser.AlertTxn alert = AlertParser.parse(msg.address(), msg.body(), msg.date()).orElse(null);
            if (alert == null) continue;
            String externalId = "sms:" + hash(msg.address(), msg.body(), msg.date());
            if (!seen.add(externalId)) continue;
            parsed.add(toTxnMap(alert, "sms", externalId));
        }
        return finishParse(userId, parsed, null, false);
    }

    /* ----------------------------------------------------------------- helpers */

    /** Filters already-imported external ids, optionally saves to an account. */
    private Map<String, Object> finishParse(UUID userId, List<Map<String, Object>> parsed,
                                            UUID accountId, boolean save) {
        Set<String> existing = existingExternalIds(userId, parsed);
        List<Map<String, Object>> fresh = parsed.stream()
                .filter(m -> !existing.contains(m.get("external_id")))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", fresh);
        // Messages already imported on an earlier sync (the unique partial index
        // on (user_id, external_id) is the source of truth on the save path).
        out.put("skipped", existing.size());
        out.put("saved", 0);
        if (save && !fresh.isEmpty()) {
            UUID account = accountId != null ? accountId : defaultAccountId(userId);
            if (account == null) {
                throw AppException.badRequest("Create a bank account first so synced transactions have somewhere to go");
            }
            List<Txn> rows = fresh.stream().map(m -> fromParsed(userId, account, m)).toList();
            List<Map<String, Object>> saved = txns.saveAll(rows).stream().map(this::toTxnRow).toList();
            out.put("data", saved);
            out.put("saved", saved.size());
        }
        return out;
    }

    private Set<String> existingExternalIds(UUID userId, List<Map<String, Object>> parsed) {
        List<String> ids = parsed.stream()
                .map(m -> (String) m.get("external_id"))
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) return Set.of();
        return txns.findExternalIdsByUserId(userId, ids);
    }

    private Txn fromParsed(UUID userId, UUID accountId, Map<String, Object> m) {
        Txn t = new Txn();
        t.setUserId(userId);
        t.setAccountId(accountId);
        t.setCategoryKey((String) m.get("category_key"));
        t.setTitle((String) m.get("title"));
        t.setNote((String) m.get("note"));
        t.setAmount((Integer) m.get("amount"));
        t.setDate(Instant.parse((String) m.get("date")));
        t.setSource((String) m.get("source"));
        t.setConfidence(((Number) m.get("confidence")) == null ? null
                : java.math.BigDecimal.valueOf(((Number) m.get("confidence")).doubleValue()));
        t.setExternalId((String) m.get("external_id"));
        t.setCreatedAt(Instant.now());
        return t;
    }

    private UUID defaultAccountId(UUID userId) {
        return accounts.findByUserId(userId).stream().findFirst().map(Account::getId).orElse(null);
    }

    private String ensureAccessToken(SyncConnection conn) {
        String refresh = crypto.decrypt(conn.getRefreshTokenEncrypted());
        if (refresh == null || refresh.isBlank()) {
            throw AppException.upstream("Gmail refresh token is missing or unreadable — reconnect Gmail");
        }
        boolean expired = conn.getTokenExpiresAt() == null
                || !conn.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(30));
        if (!expired && conn.getAccessTokenEncrypted() != null) {
            String token = crypto.decrypt(conn.getAccessTokenEncrypted());
            if (token != null && !token.isBlank()) {
                return token;
            }
        }
        GmailService.TokenResponse refreshed = gmail.refreshAccessToken(refresh);
        conn.setAccessTokenEncrypted(crypto.encrypt(refreshed.accessToken()));
        conn.setTokenExpiresAt(refreshed.expiresIn() == null ? null : Instant.now().plusSeconds(refreshed.expiresIn() - 60));
        conn.setUpdatedAt(Instant.now());
        connections.save(conn);
        return refreshed.accessToken();
    }

    /** Builds the client-facing transaction map (same shape as /import/parse). */
    private Map<String, Object> toTxnMap(AlertParser.AlertTxn alert, String source, String externalId) {
        Map<String, Object> m = imports.heuristic(new ImportService.Raw(alert.title(), alert.amount(), alert.date(), alert.note()));
        m.put("source", source);
        m.put("external_id", externalId);
        return m;
    }

    private Map<String, Object> toTxnRow(Txn t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("user_id", t.getUserId());
        m.put("account_id", t.getAccountId());
        m.put("category_key", t.getCategoryKey());
        m.put("title", t.getTitle());
        m.put("note", t.getNote());
        m.put("amount", t.getAmount());
        m.put("date", t.getDate());
        m.put("source", t.getSource());
        m.put("confidence", t.getConfidence());
        m.put("external_id", t.getExternalId());
        m.put("created_at", t.getCreatedAt());
        return m;
    }

    private static String join(String subject, String body) {
        StringBuilder sb = new StringBuilder();
        if (subject != null && !subject.isBlank()) sb.append(subject).append(" ");
        if (body != null) sb.append(body);
        return sb.toString();
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part != null) md.update(part.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0x1f);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.nanoTime());
        }
    }

    private static String htmlPage(String title, String message) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + title + "</title></head>"
                + "<body style=\"font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#F8FAFC;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0\">"
                + "<div style=\"background:#fff;border:1px solid #E2E8F0;border-radius:16px;padding:32px;max-width:420px;text-align:center\">"
                + "<div style=\"font-size:40px\">" + (title.contains("connected") ? "✅" : "⚠️") + "</div>"
                + "<h1 style=\"font-size:18px;color:#0F172A;margin:12px 0 8px\">" + title + "</h1>"
                + "<p style=\"color:#64748B;font-size:14px;line-height:1.5;margin:0\">" + message + "</p>"
                + "</div></body></html>";
    }

    /** Raw SMS message from the device (read with a native module). */
    public record SmsMessage(String id, String address, String body, String date) {}
}