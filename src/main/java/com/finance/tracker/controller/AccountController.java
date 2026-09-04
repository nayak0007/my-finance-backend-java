package com.finance.tracker.controller;

import com.finance.tracker.dto.AccountDtos;
import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("data", accounts.list(AuthSupport.currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody AccountDtos.CreateAccountRequest req) {
        return accounts.create(AuthSupport.currentUserId(), req);
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody AccountDtos.UpdateAccountRequest req) {
        return accounts.update(AuthSupport.currentUserId(), id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean force) {
        return accounts.remove(AuthSupport.currentUserId(), id, force);
    }
}
