package com.eventledger.gateway.service;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;

import java.util.List;

public interface EventService {

    EventResponse processEvent(EventRequest request);

    EventResponse getEventById(String eventId);

    List<EventResponse> getEventsByAccount(String accountId);
}