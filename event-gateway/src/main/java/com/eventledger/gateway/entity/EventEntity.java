package com.eventledger.gateway.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "events",
        indexes = {
                @Index(name = "idx_account_timestamp", columnList = "account_id, event_timestamp")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private EventType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}