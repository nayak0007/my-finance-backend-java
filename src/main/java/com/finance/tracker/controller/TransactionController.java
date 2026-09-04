package com.finance.tracker.controller;

import com.finance.tracker.dto.TxnDtos;
import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionService txns;

    public TransactionController(TransactionService txns) {
        this.txns = txns;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String category,
            @RequestParam(value = "account_id", required = false) UUID accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit
    ) {
        int capped = Math.min(Math.max(limit, 1), 100);
        return txns.list(AuthSupport.currentUserId(), from, to, category, accountId, cursor, capped);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody TxnDtos.CreateTxnRequest req) {
        return txns.create(AuthSupport.currentUserId(), req);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> bulk(@Valid @RequestBody TxnDtos.BulkTxnRequest req) {
        return txns.bulk(AuthSupport.currentUserId(), req.transactions());
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody TxnDtos.UpdateTxnRequest req) {
        return txns.update(AuthSupport.currentUserId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        txns.remove(AuthSupport.currentUserId(), id);
    }
}
