package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allows React running on any port to call these API endpoints
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventRequest request) {
        log.info("Received transaction event submission for eventId: {}, accountId: {}",
                request.getEventId(), request.getAccountId());

        EventResponse response = eventService.processEvent(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable("id") String id) {
        log.info("Fetching event by ID: {}", id);
        EventResponse response = eventService.getEventById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByAccount(@RequestParam("account") String accountId) {
        log.info("Fetching events for accountId: {}", accountId);
        List<EventResponse> response = eventService.getEventsByAccount(accountId);
        return ResponseEntity.ok(response);
    }
}