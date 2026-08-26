# Event Ledger System

A microservices-based event ledger system composed of two services that work together to process financial transaction events.

## Architecture

```
┌──────────────────────┐
Browser / Client ──────→ │ Event Gateway API │ (Port 8080)
                        │ (public-facing)   │
                        └──────┬────────────┘
                               │ REST (sync)
                               ▼
                        ┌──────────────────────┐
                        │ Account Service      │ (Port 8081)
                        │ (internal)           │
                        └──────────────────────┘
```

## Features

### Core Functionality
- **Idempotency**: Duplicate event submissions return the original event without side effects
- **Out-of-order tolerance**: Events are stored and listed in chronological order by eventTimestamp
- **Balance computation**: Net balance = sum of CREDITs − sum of DEBITs
- **Validation**: Comprehensive validation of event payloads

### Observability
- **Distributed Tracing**: Trace ID propagation via X-Trace-Id header
- **Structured Logging**: JSON-formatted logs with trace ID in production
- **Health Checks**: `/health` endpoints with database connectivity status
- **Custom Metrics**: Request counters and failure metrics via Micrometer

### Resiliency
- **Circuit Breaker**: Resilience4j circuit breaker on Account Service calls
- **Graceful Degradation**: GET endpoints work even when Account Service is unavailable
- **Timeout Configuration**: Configurable timeouts for downstream calls

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker and Docker Compose (optional)

## Quick Start

### Option 1: Using Docker Compose (Recommended)

```bash
# From the project root directory
docker-compose up --build
```

Services will be available at:
- Event Gateway: http://localhost:8080
- Account Service: http://localhost:8081

### Option 2: Running Locally

**Terminal 1 - Start Account Service:**
```bash
cd account-service/account-service
./mvnw spring-boot:run
```

**Terminal 2 - Start Event Gateway:**
```bash
cd event-gateway/event-gateway
./mvnw spring-boot:run
```

## API Endpoints

### Event Gateway (Port 8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/events` | Submit a transaction event |
| GET | `/events/{id}` | Retrieve a single event by ID |
| GET | `/events?account={accountId}` | List events for an account |
| GET | `/health` | Health check |

### Account Service (Port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/accounts/{accountId}/transactions` | Apply a transaction |
| GET | `/accounts/{accountId}` | Get account details |
| GET | `/accounts/{accountId}/balance` | Get current balance |
| GET | `/accounts/{accountId}/history` | Get transaction history |
| GET | `/health` | Health check |

## Example Usage

### Submit a Transaction Event

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 150.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T14:02:11Z",
    "metadata": {
      "source": "mainframe-batch",
      "batchId": "B-9042"
    }
  }'
```

### Get Event by ID

```bash
curl http://localhost:8080/events/evt-001
```

### Get Events by Account

```bash
curl "http://localhost:8080/events?account=acct-123"
```

### Get Account Balance

```bash
curl http://localhost:8081/accounts/acct-123/balance
```

### Health Check

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

## Running Tests

```bash
# Event Gateway tests
cd event-gateway/event-gateway
./mvnw test

# Account Service tests
cd account-service/account-service
./mvnw test
```

## Configuration

### Event Gateway (application.properties)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port |
| `account.service.url` | http://localhost:8081 | Account Service URL |
| `resilience4j.circuitbreaker.instances.accountService.failure-rate-threshold` | 50 | Circuit breaker threshold |

### Account Service (application.properties)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8081 | Server port |

## Design Decisions

### Circuit Breaker Pattern
Implemented Resilience4j circuit breaker to handle Account Service failures gracefully:
- Opens after 50% failure rate (configurable)
- Half-open state allows 3 test calls
- 10-second wait duration in open state

### Idempotency
- Events are keyed by `eventId`
- Duplicate submissions return the original event
- Account Service also checks for duplicate transactions

### Trace Propagation
- Trace ID generated at Gateway using Micrometer Tracing
- Propagated to Account Service via `X-Trace-Id` header
- Both services log trace ID in structured format

### Graceful Degradation
- GET endpoints in Gateway work independently of Account Service
- POST returns 503 when Account Service is unavailable
- Circuit breaker prevents cascade failures

## H2 Console Access

For debugging, H2 Console is available:
- Event Gateway: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:gatewaydb`)
- Account Service: http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:accountdb`)
