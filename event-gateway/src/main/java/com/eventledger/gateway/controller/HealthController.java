package com.eventledger.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        log.info("Health check requested for Event Gateway");
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "event-gateway"
        ));
    }
}