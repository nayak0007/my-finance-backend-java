package com.finance.tracker.service;

import com.finance.tracker.domain.Account;
import com.finance.tracker.dto.AccountDtos;
import com.finance.tracker.exception.AppException;
import com.finance.tracker.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public List<Map<String, Object>> list(UUID userId) {
        return accounts.findByUserId(userId).stream().map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> create(UUID userId, AccountDtos.CreateAccountRequest req) {
        Account a = new Account();
        a.setUserId(userId);
        a.setBank(req.bank());
        a.setName(req.name());
        a.setMask(req.mask());
        a.setType(req.type());
        a.setBalance(req.balance() == null ? 0 : req.balance());
        a.setChangePct(req.changePct() == null ? BigDecimal.ZERO : req.changePct());
        a.setColor(req.color() == null || req.color().isBlank() ? "#4F46E5" : req.color());
        a.setInflow(req.inflow() == null ? 0 : req.inflow());
        a.setOutflow(req.outflow() == null ? 0 : req.outflow());
        a.setStatus(req.status() == null ? "connected" : req.status());
        a.setLastSyncedAt(Instant.now());
        a.setCreatedAt(Instant.now());
        return toMap(accounts.save(a));
    }

    @Transactional
    public Map<String, Object> update(UUID userId, UUID id, AccountDtos.UpdateAccountRequest req) {
        Account a = accounts.findByUserIdAndId(userId, id).orElseThrow(() -> AppException.notFound("Account not found"));
        if (req.bank() != null) a.setBank(req.bank());
        if (req.name() != null) a.setName(req.name());
        if (req.mask() != null) a.setMask(req.mask());
        if (req.type() != null) a.setType(req.type());
        if (req.balance() != null) a.setBalance(req.balance());
        if (req.changePct() != null) a.setChangePct(req.changePct());
        if (req.color() != null) a.setColor(req.color());
        if (req.inflow() != null) a.setInflow(req.inflow());
        if (req.outflow() != null) a.setOutflow(req.outflow());
        if (req.status() != null) a.setStatus(req.status());
        a.setLastSyncedAt(Instant.now());
        return toMap(accounts.save(a));
    }

    @Transactional
    public Map<String, Object> remove(UUID userId, UUID id, boolean force) {
        Account a = accounts.findByUserIdAndId(userId, id).orElseThrow(() -> AppException.notFound("Account not found"));
        long txnCount = accounts.countTransactions(userId, id);
        if (txnCount > 0 && !force) {
            throw AppException.conflict(
                    "This account has " + txnCount + " transaction" + (txnCount == 1 ? "" : "s")
                            + ". Confirm to delete them along with the account.");
        }
        accounts.delete(a);
        return Map.of("ok", true, "deleted_transactions", txnCount);
    }

    public Account requireOwned(UUID userId, UUID id) {
        return accounts.findByUserIdAndId(userId, id).orElseThrow(() -> AppException.notFound("Account not found"));
    }

    private Map<String, Object> toMap(Account a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("user_id", a.getUserId());
        m.put("bank", a.getBank());
        m.put("name", a.getName());
        m.put("mask", a.getMask());
        m.put("type", a.getType());
        m.put("balance", a.getBalance());
        m.put("change_pct", a.getChangePct());
        m.put("color", a.getColor());
        m.put("inflow", a.getInflow());
        m.put("outflow", a.getOutflow());
        m.put("status", a.getStatus());
        m.put("last_synced_at", a.getLastSyncedAt());
        m.put("created_at", a.getCreatedAt());
        return m;
    }
}
