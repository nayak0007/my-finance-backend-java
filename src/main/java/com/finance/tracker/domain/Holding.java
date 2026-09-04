package com.finance.tracker.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "holdings")
public class Holding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String issuer;

    @Column(nullable = false, columnDefinition = "holding_klass")
    private String klass;

    @Column(nullable = false)
    private Integer value = 0;

    @Column(nullable = false)
    private Integer invested = 0;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal xirr = BigDecimal.ZERO;

    @Column(nullable = false)
    private String color = "#4F46E5";

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getKlass() { return klass; }
    public void setKlass(String klass) { this.klass = klass; }
    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }
    public Integer getInvested() { return invested; }
    public void setInvested(Integer invested) { this.invested = invested; }
    public BigDecimal getXirr() { return xirr; }
    public void setXirr(BigDecimal xirr) { this.xirr = xirr; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
