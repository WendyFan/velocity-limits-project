package com.venn.velocity.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "load_records", indexes = {
    @Index(name = "idx_customer_time", columnList = "customerId, loadTime"),
    @Index(name = "idx_load_customer", columnList = "loadId, customerId")
})
public class LoadRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dbId;

    @Column(nullable = false)
    private String loadId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant loadTime;

    @Column(nullable = false)
    private boolean accepted;

    protected LoadRecord() {}

    public LoadRecord(String loadId, String customerId, BigDecimal amount, Instant loadTime, boolean accepted) {
        this.loadId = loadId;
        this.customerId = customerId;
        this.amount = amount;
        this.loadTime = loadTime;
        this.accepted = accepted;
    }

    public Long getDbId() { return dbId; }
    public String getLoadId() { return loadId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getLoadTime() { return loadTime; }
    public boolean isAccepted() { return accepted; }
}
