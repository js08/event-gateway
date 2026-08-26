package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.AccountResponse;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.TransactionRequest;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final Counter accountServiceCallsCounter;
    private final Counter accountServiceFailuresCounter;

    @Value("${account.service.url:http://localhost:8081}")
    private String accountServiceUrl;

    public AccountServiceClient(RestTemplate restTemplate, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.accountServiceCallsCounter = Counter.builder("account_service_calls_total")
                .description("Total calls to Account Service")
                .register(meterRegistry);
        this.accountServiceFailuresCounter = Counter.builder("account_service_failures_total")
                .description("Total failed calls to Account Service")
                .register(meterRegistry);
    }

    /**
     * Calls Account Service POST /accounts/{accountId}/transactions.
     * Protected by Resilience4j Circuit Breaker named "accountService".
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "processTransactionFallback")
    public void processTransaction(EventRequest request) {
        accountServiceCallsCounter.increment();
        String url = String.format("%s/accounts/%s/transactions", accountServiceUrl, request.getAccountId());
        log.info("Sending transaction to Account Service at URL: {}", url);

        // Convert EventRequest to TransactionRequest (Account Service expects this format)
        TransactionRequest transactionRequest = TransactionRequest.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .build();

        try {
            restTemplate.postForEntity(url, transactionRequest, Void.class);
            log.info("Successfully synced eventId {} with Account Service", request.getEventId());
        } catch (Exception e) {
            log.error("Failed to call Account Service for eventId: {}. Error: {}", 
                request.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Resilience4j Fallback method executed when Circuit Breaker is OPEN or call fails.
     */
    public void processTransactionFallback(EventRequest request, Throwable throwable) {
        accountServiceFailuresCounter.increment();
        log.error("Account Service call failed for eventId: {}, accountId: {}. Reason: {}",
                request.getEventId(), request.getAccountId(), throwable.getMessage());

        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable. Event was stored but not applied to account balance."
        );
    }

    /**
     * Get account balance from Account Service.
     * Protected by Circuit Breaker.
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountBalanceFallback")
    public AccountResponse getAccountBalance(String accountId) {
        accountServiceCallsCounter.increment();
        String url = String.format("%s/accounts/%s", accountServiceUrl, accountId);
        log.info("Fetching balance from Account Service at: {}", url);

        return restTemplate.getForObject(url, AccountResponse.class);
    }

    public AccountResponse getAccountBalanceFallback(String accountId, Throwable throwable) {
        accountServiceFailuresCounter.increment();
        log.error("Failed to fetch balance for accountId: {}. Reason: {}", accountId, throwable.getMessage());

        throw new AccountServiceUnavailableException(
                "Account Service is currently unavailable. Cannot retrieve balance."
        );
    }
}