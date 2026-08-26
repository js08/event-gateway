package com.eventledger.gateway.dto;

import com.eventledger.gateway.entity.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
    private String eventId;
    private String accountId;
    private EventType type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
}
