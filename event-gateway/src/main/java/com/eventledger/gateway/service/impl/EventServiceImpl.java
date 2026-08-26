package com.eventledger.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.ResourceNotFoundException;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.gateway.service.EventService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;
    private final Counter eventsReceivedCounter;
    private final Counter duplicateEventsCounter;

    public EventServiceImpl(EventRepository eventRepository, 
                           AccountServiceClient accountServiceClient,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
        this.eventsReceivedCounter = Counter.builder("events_received_total")
                .description("Total events received by Gateway")
                .register(meterRegistry);
        this.duplicateEventsCounter = Counter.builder("duplicate_events_total")
                .description("Total duplicate events detected")
                .register(meterRegistry);
    }

    @Override
    public EventResponse processEvent(EventRequest request) {
        eventsReceivedCounter.increment();
        log.info("Processing eventId: {} for accountId: {}", request.getEventId(), request.getAccountId());

        // 1. Idempotency Check: Return existing event if eventId was already submitted
        Optional<EventEntity> existingEntity = eventRepository.findById(request.getEventId());
        if (existingEntity.isPresent()) {
            duplicateEventsCounter.increment();
            log.warn("Duplicate event submission detected for eventId: {}. Returning stored event (Idempotent response).", request.getEventId());
            return mapToResponse(existingEntity.get());
        }

        // 2. Persist Event locally in Gateway DB FIRST (in separate transaction)
        EventEntity savedEntity = persistEvent(request);
        log.info("EventId: {} successfully stored in local Gateway DB", savedEntity.getEventId());

        // 3. Synchronize with Account Service (Triggers Circuit Breaker if Account Service is DOWN)
        // This will throw AccountServiceUnavailableException if the service is down
        // But the event is already saved, so it won't be lost
        accountServiceClient.processTransaction(request);

        return mapToResponse(savedEntity);
    }

    @Transactional
    private EventEntity persistEvent(EventRequest request) {
        EventEntity entity = EventEntity.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .metadataJson(serializeMetadata(request.getMetadata()))
                .build();

        return eventRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(String eventId) {
        EventEntity entity = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
        return mapToResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByAccount(String accountId) {
        // Retrieves events ordered chronologically by eventTimestamp
        List<EventEntity> entities = eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        return entities.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // --- Helper Mapping Methods ---

    private EventResponse mapToResponse(EventEntity entity) {
        return EventResponse.builder()
                .eventId(entity.getEventId())
                .accountId(entity.getAccountId())
                .type(entity.getType())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .eventTimestamp(entity.getEventTimestamp())
                .createdAt(entity.getCreatedAt())
                .metadata(deserializeMetadata(entity.getMetadataJson()))
                .build();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata to JSON", e);
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize metadata JSON", e);
            return Collections.emptyMap();
        }
    }
}