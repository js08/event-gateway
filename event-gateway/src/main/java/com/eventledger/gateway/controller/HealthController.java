package com.eventledger.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("Health check requested for Event Gateway");
        
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "event-gateway");
        
        // Check database connectivity
        try (Connection conn = dataSource.getConnection()) {
            health.put("database", Map.of(
                "status", "UP",
                "type", "H2",
                "valid", conn.isValid(2)
            ));
        } catch (Exception e) {
            log.error("Database health check failed", e);
            health.put("database", Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
            health.put("status", "DOWN");
        }
        
        return ResponseEntity.ok(health);
    }
}