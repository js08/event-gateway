//package com.eventledger.gateway.client;
//
//import com.eventledger.gateway.dto.EventRequest;
//import com.eventledger.gateway.exception.AccountServiceUnavailableException;
//import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class AccountServiceClient {
//
//    private final RestTemplate restTemplate;
//
//    @Value("${account.service.url:http://localhost:8081}")
//    private String accountServiceUrl;
//
//    /**
//     * Calls Account Service POST /accounts/{accountId}/transactions.
//     * Protected by Resilience4j Circuit Breaker named "accountService".
//     */
//    @CircuitBreaker(name = "accountService", fallbackMethod = "processTransactionFallback")
//    public void processTransaction(EventRequest request) {
//        String url = String.format("%s/accounts/%s/transactions", accountServiceUrl, request.getAccountId());
//        log.info("Sending transaction to Account Service at URL: {}", url);
//
//        restTemplate.postForEntity(url, request, Void.class);
//        log.info("Successfully synced eventId {} with Account Service", request.getEventId());
//    }
//
//    /**
//     * Resilience4j Fallback method executed when Circuit Breaker is OPEN or call fails.
//     */
//    public void processTransactionFallback(EventRequest request, Throwable throwable) {
//        log.error("Account Service call failed for eventId: {}, accountId: {}. Reason: {}",
//                request.getEventId(), request.getAccountId(), throwable.getMessage());
//
//        throw new AccountServiceUnavailableException(
//                "Account Service is currently unavailable. Event was not applied to account balance."
//        );
//    }
//}


package com.eventledger.gateway.client; // Package location

import com.eventledger.gateway.dto.AccountResponse;
import com.eventledger.gateway.dto.EventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j // Generates logger
@Component // Marks class as a Spring Bean
@RequiredArgsConstructor // Automatically injects configured RestTemplate Bean
public class AccountServiceClient {

    // Spring injects the RestTemplate bean created in RestTemplateConfig (with trace interceptor)
    private final RestTemplate restTemplate;

    @Value("${account.service.url:http://localhost:8081}")
    private String accountServiceUrl;

    public void forwardTransaction(String accountId, EventRequest request) {
        String url = accountServiceUrl + "/accounts/" + accountId + "/transactions";

        log.info("Forwarding transaction to Account Service at: {}", url);

        // Outgoing POST call automatically passes through traceHeaderInterceptor
        restTemplate.postForEntity(url, request, Void.class);
    }

    public AccountResponse getAccountBalance(String accountId) {
        String url = accountServiceUrl + "/accounts/" + accountId;

        log.info("Fetching balance from Account Service at: {}", url);

        // Outgoing GET call automatically passes through traceHeaderInterceptor
        return restTemplate.getForObject(url, AccountResponse.class);
    }
}