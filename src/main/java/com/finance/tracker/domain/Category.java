package com.finance.tracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, columnDefinition = "category_kind")
    private String kind;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
}
