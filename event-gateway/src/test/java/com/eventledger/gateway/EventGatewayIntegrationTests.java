package com.eventledger.gateway;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.entity.EventType;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventGatewayIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @MockBean
    private AccountServiceClient accountServiceClient;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /events - Event Submission")
    class EventSubmissionTests {

        @Test
        @DisplayName("Should successfully create a new event")
        void shouldCreateNewEvent() throws Exception {
            EventRequest request = createEventRequest("evt-001", "acct-123", EventType.CREDIT, "100.00");
            doNothing().when(accountServiceClient).processTransaction(any());

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventId").value("evt-001"))
                    .andExpect(jsonPath("$.accountId").value("acct-123"))
                    .andExpect(jsonPath("$.type").value("CREDIT"))
                    .andExpect(jsonPath("$.amount").value(100.00));

            verify(accountServiceClient, times(1)).processTransaction(any());
        }

        @Test
        @DisplayName("Should handle idempotent duplicate submission")
        void shouldHandleIdempotentDuplicateSubmission() throws Exception {
            EventRequest request = createEventRequest("evt-dup-001", "acct-123", EventType.CREDIT, "100.00");
            doNothing().when(accountServiceClient).processTransaction(any());

            // First submission
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Duplicate submission - should return same event
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventId").value("evt-dup-001"));

            // Account service should only be called once (idempotency)
            verify(accountServiceClient, times(1)).processTransaction(any());
        }

        @Test
        @DisplayName("Should reject event with missing required fields")
        void shouldRejectEventWithMissingFields() throws Exception {
            String invalidRequest = """
                {
                    "accountId": "acct-123",
                    "type": "CREDIT",
                    "amount": 100.00
                }
                """;

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject event with zero amount")
        void shouldRejectEventWithZeroAmount() throws Exception {
            EventRequest request = createEventRequest("evt-zero", "acct-123", EventType.CREDIT, "0.00");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject event with negative amount")
        void shouldRejectEventWithNegativeAmount() throws Exception {
            EventRequest request = createEventRequest("evt-neg", "acct-123", EventType.DEBIT, "-50.00");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /events - Event Retrieval")
    class EventRetrievalTests {

        @Test
        @DisplayName("Should retrieve event by ID")
        void shouldRetrieveEventById() throws Exception {
            // First create an event
            EventRequest request = createEventRequest("evt-get-001", "acct-123", EventType.CREDIT, "150.00");
            doNothing().when(accountServiceClient).processTransaction(any());

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Then retrieve it
            mockMvc.perform(get("/events/evt-get-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-get-001"))
                    .andExpect(jsonPath("$.amount").value(150.00));
        }

        @Test
        @DisplayName("Should return 404 for non-existent event")
        void shouldReturn404ForNonExistentEvent() throws Exception {
            mockMvc.perform(get("/events/non-existent-id"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should retrieve events by account ordered by timestamp")
        void shouldRetrieveEventsByAccountOrderedByTimestamp() throws Exception {
            doNothing().when(accountServiceClient).processTransaction(any());

            // Create events with different timestamps (out of order)
            EventRequest event3 = createEventRequestWithTimestamp("evt-003", "acct-order", EventType.CREDIT, "300.00", "2026-05-15T16:00:00Z");
            EventRequest event1 = createEventRequestWithTimestamp("evt-001", "acct-order", EventType.CREDIT, "100.00", "2026-05-15T14:00:00Z");
            EventRequest event2 = createEventRequestWithTimestamp("evt-002", "acct-order", EventType.DEBIT, "200.00", "2026-05-15T15:00:00Z");

            // Submit in wrong order
            mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(event3)));
            mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(event1)));
            mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(event2)));

            // Retrieve - should be in timestamp order
            mockMvc.perform(get("/events").param("account", "acct-order"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].eventId").value("evt-001"))
                    .andExpect(jsonPath("$[1].eventId").value("evt-002"))
                    .andExpect(jsonPath("$[2].eventId").value("evt-003"));
        }
    }

    @Nested
    @DisplayName("Resiliency - Circuit Breaker")
    class ResiliencyTests {

        @Test
        @DisplayName("Should return 503 when Account Service is unavailable")
        void shouldReturn503WhenAccountServiceUnavailable() throws Exception {
            EventRequest request = createEventRequest("evt-fail-001", "acct-123", EventType.CREDIT, "100.00");
            
            doThrow(new AccountServiceUnavailableException("Account Service is currently unavailable"))
                    .when(accountServiceClient).processTransaction(any());

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("Service Unavailable"));
        }

        @Test
        @DisplayName("GET endpoints should work even when Account Service is unavailable")
        void getEndpointsShouldWorkWhenAccountServiceUnavailable() throws Exception {
            // First create an event successfully
            EventRequest request = createEventRequest("evt-resilient", "acct-resilient", EventType.CREDIT, "100.00");
            doNothing().when(accountServiceClient).processTransaction(any());

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Now simulate Account Service failure
            doThrow(new AccountServiceUnavailableException("Account Service down"))
                    .when(accountServiceClient).processTransaction(any());

            // GET /events/{id} should still work (uses local data)
            mockMvc.perform(get("/events/evt-resilient"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-resilient"));

            // GET /events?account= should still work (uses local data)
            mockMvc.perform(get("/events").param("account", "acct-resilient"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].eventId").value("evt-resilient"));
        }
    }

    @Nested
    @DisplayName("Health Check")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return health status")
        void shouldReturnHealthStatus() throws Exception {
            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.service").value("event-gateway"));
        }
    }

    // Helper methods
    private EventRequest createEventRequest(String eventId, String accountId, EventType type, String amount) {
        return EventRequest.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type(type)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadata(Map.of("source", "test"))
                .build();
    }

    private EventRequest createEventRequestWithTimestamp(String eventId, String accountId, EventType type, String amount, String timestamp) {
        return EventRequest.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type(type)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .eventTimestamp(Instant.parse(timestamp))
                .metadata(Map.of("source", "test"))
                .build();
    }
}
