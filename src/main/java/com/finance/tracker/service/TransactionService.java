package com.finance.tracker.service;

import com.finance.tracker.domain.Txn;
import com.finance.tracker.dto.TxnDtos;
import com.finance.tracker.exception.AppException;
import com.finance.tracker.repository.AccountRepository;
import com.finance.tracker.repository.TxnRepository;
import com.finance.tracker.util.HashUtil;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TxnRepository txns;
    private final AccountRepository accounts;

    public TransactionService(TxnRepository txns, AccountRepository accounts) {
        this.txns = txns;
        this.accounts = accounts;
    }

    public Map<String, Object> list(UUID userId, Instant from, Instant to, String category, UUID accountId, String cursor, int limit) {
        log.debug("txns list user={} from={} to={} category={} accountId={} hasCursor={} limit={}",
                userId, from, to, category, accountId, cursor != null && !cursor.isBlank(), limit);
        HashUtil.Cursor decoded = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                decoded = HashUtil.decodeCursor(cursor);
            } catch (Exception ignored) {
            }
        }
        HashUtil.Cursor c = decoded;
        Specification<Txn> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("userId"), userId));
            if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            if (to != null) preds.add(cb.lessThanOrEqualTo(root.get("date"), to));
            if (category != null) preds.add(cb.equal(root.get("categoryKey"), category));
            if (accountId != null) preds.add(cb.equal(root.get("accountId"), accountId));
            if (c != null) {
                preds.add(cb.or(
                        cb.lessThan(root.get("date"), c.date()),
                        cb.and(cb.equal(root.get("date"), c.date()), cb.lessThan(root.get("id"), c.id()))
                ));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
        List<Txn> rows = txns.findAll(
                spec, PageRequest.of(0, limit + 1, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")))).getContent();
        boolean hasMore = rows.size() > limit;
        List<Txn> page = hasMore ? rows.subList(0, limit) : rows;
        String next = null;
        if (hasMore && !page.isEmpty()) {
            Txn last = page.get(page.size() - 1);
            next = HashUtil.encodeCursor(last.getDate(), last.getId());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", page.stream().map(this::toMap).toList());
        out.put("next_cursor", next);
        log.debug("txns list ok user={} count={} hasMore={}", userId, page.size(), hasMore);
        return out;
    }

    @Transactional
    public Map<String, Object> create(UUID userId, TxnDtos.CreateTxnRequest req) {
        log.info("txns create user={} accountId={} category={} amount={}", userId, req.accountId(), req.categoryKey(), req.amount());
        accounts.findByUserIdAndId(userId, req.accountId()).orElseThrow(() -> AppException.notFound("Account not found"));
        Map<String, Object> created = toMap(txns.save(fromCreate(userId, req)));
        log.info("txns create ok user={} id={}", userId, created.get("id"));
        return created;
    }

    @Transactional
    public Map<String, Object> bulk(UUID userId, List<TxnDtos.CreateTxnRequest> requests) {
        log.info("txns bulk user={} requested={}", userId, requests.size());
        Set<UUID> allowed = new HashSet<>(accounts.findByUserId(userId).stream().map(a -> a.getId()).toList());
        Set<String> existingExternal = new HashSet<>(existingExternalIds(userId, requests));
        List<Txn> valid = requests.stream()
                .filter(r -> allowed.contains(r.accountId()))
                .filter(r -> r.externalId() == null || r.externalId().isBlank() || !existingExternal.contains(r.externalId()))
                .map(r -> fromCreate(userId, r))
                .toList();
        if (valid.isEmpty()) {
            log.warn("txns bulk no matching/unique accounts user={} requested={} existingExternal={}",
                    userId, requests.size(), existingExternal.size());
            throw AppException.notFound("No matching accounts for bulk import");
        }
        List<Map<String, Object>> saved = txns.saveAll(valid).stream().map(this::toMap).toList();
        log.info("txns bulk ok user={} saved={} skippedDuplicates={}", userId, saved.size(), existingExternal.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", saved);
        out.put("skipped", existingExternal.size());
        return out;
    }

    /**
     * External ids (Gmail message id / SMS content hash) that already exist for
     * this user, used to skip re-importing synced messages.
     */
    public Set<String> existingExternalIds(UUID userId, Collection<TxnDtos.CreateTxnRequest> requests) {
        List<String> ids = requests.stream()
                .map(TxnDtos.CreateTxnRequest::externalId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) return Set.of();
        return txns.findExternalIdsByUserId(userId, ids);
    }

    public List<Txn> findByExternalIds(UUID userId, Collection<String> externalIds) {
        List<String> ids = externalIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return txns.findByUserIdAndExternalIdIn(userId, ids);
    }

    @Transactional
    public Map<String, Object> update(UUID userId, UUID id, TxnDtos.UpdateTxnRequest req) {
        log.info("txns update user={} id={}", userId, id);
        Txn t = txns.findByUserIdAndId(userId, id).orElseThrow(() -> AppException.notFound("Transaction not found"));
        if (req.accountId() != null) {
            accounts.findByUserIdAndId(userId, req.accountId()).orElseThrow(() -> AppException.notFound("Account not found"));
            t.setAccountId(req.accountId());
        }
        if (req.categoryKey() != null) t.setCategoryKey(req.categoryKey());
        if (req.title() != null) t.setTitle(req.title());
        if (req.note() != null) t.setNote(req.note());
        if (req.amount() != null) t.setAmount(req.amount());
        if (req.date() != null) t.setDate(req.date());
        if (req.source() != null) t.setSource(req.source());
        if (req.confidence() != null) t.setConfidence(req.confidence());
        return toMap(txns.save(t));
    }

    @Transactional
    public void remove(UUID userId, UUID id) {
        log.info("txns delete user={} id={}", userId, id);
        Txn t = txns.findByUserIdAndId(userId, id).orElseThrow(() -> AppException.notFound("Transaction not found"));
        txns.delete(t);
        log.info("txns delete ok user={} id={}", userId, id);
    }

    private Txn fromCreate(UUID userId, TxnDtos.CreateTxnRequest req) {
        Txn t = new Txn();
        t.setUserId(userId);
        t.setAccountId(req.accountId());
        t.setCategoryKey(req.categoryKey());
        t.setTitle(req.title());
        t.setNote(req.note());
        t.setAmount(req.amount());
        t.setDate(req.date());
        t.setSource(req.source() == null ? "manual" : req.source());
        t.setConfidence(req.confidence());
        t.setExternalId(req.externalId() == null || req.externalId().isBlank() ? null : req.externalId());
        t.setCreatedAt(Instant.now());
        return t;
    }

    private Map<String, Object> toMap(Txn t) {
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
        m.put("confidence", t.getConfidence() == null ? null : t.getConfidence());
        m.put("external_id", t.getExternalId());
        m.put("created_at", t.getCreatedAt());
        return m;
    }
}
