package com.finance.tracker.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String bank;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 8)
    private String mask;

    @Column(nullable = false, columnDefinition = "account_type")
    private String type;

    @Column(nullable = false)
    private Integer balance = 0;

    @Column(name = "change_pct", nullable = false, precision = 8, scale = 2)
    private BigDecimal changePct = BigDecimal.ZERO;

    @Column(nullable = false)
    private String color = "#4F46E5";

    @Column(nullable = false)
    private Integer inflow = 0;

    @Column(nullable = false)
    private Integer outflow = 0;

    @Column(nullable = false, columnDefinition = "account_status")
    private String status = "connected";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMask() { return mask; }
    public void setMask(String mask) { this.mask = mask; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }
    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getInflow() { return inflow; }
    public void setInflow(Integer inflow) { this.inflow = inflow; }
    public Integer getOutflow() { return outflow; }
    public void setOutflow(Integer outflow) { this.outflow = outflow; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
