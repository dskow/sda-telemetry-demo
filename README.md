# SDA Telemetry Demo

A small Java/Spring Boot service that ingests Resident Space Object (RSO) telemetry
from Apache Kafka, exposes a REST catalog of tracked objects, and supports a NiFi
ingest path for sensor CSV feeds.

This is a **sustainment-oriented** demo: the focus is operational readiness --
health/readiness probes, structured logging, metrics, dead-letter queue (DLQ) handling,
runbooks, and post-incident reports -- not new feature development.

> Domain framing: Space Domain Awareness (SDA) systems track RSOs (active satellites,
> spent rocket bodies, debris) and provide indications-and-warnings (I&W) data to
> ground operators. This demo models a tiny slice of that pipeline.

## Architecture

```
+---------+          +-------+          +----------------+          +-------------+
|  CSV    | -------> | NiFi  | -------> |     Kafka      | -------> | Spring Boot |
| sensor  |          | flow  |          | rso.telemetry  |          | consumer    |
| feeds   |          |       |          | rso.tele.dlq   | <------- | (DLQ on err)|
+---------+          +-------+          +----------------+          +------+------+
                                                                            |
                                                                            v
                                                                    +---------------+
                                                                    | RSO catalog   |
                                                                    | (REST API)    |
                                                                    +---------------+
```

## What's in the box

| Capability | File(s) |
|---|---|
| REST CRUD for the RSO catalog | `RsoController.java`, `RsoCatalog.java` |
| Kafka consumer with idempotency (per RSO + sensor) | `TelemetryConsumer.java`, `RsoCatalog.applyTelemetry` |
| Producer with `acks=all` and idempotent producer enabled | `application.yml` |
| Poison-pill protection via `ErrorHandlingDeserializer` + DLQ | `application.yml`, `KafkaConfig.java` |
| Retry-then-DLQ error handling (3 retries, 1s backoff) | `KafkaConfig.java` |
| Liveness / readiness probes, Prometheus metrics | `application.yml`, actuator endpoints |
| Structured JSON logging with Kafka offset/partition in MDC | `logback-spring.xml`, `TelemetryConsumer.java` |
| Distroless non-root Docker image | `Dockerfile` |
| Local stack (Kafka + NiFi + app + Prometheus) | `docker-compose.yml` |
| NiFi CSV-to-Kafka flow | `nifi/flow.json` |
| Unit tests + embedded-Kafka integration test | `src/test/...` |
| Runbook, Standard Operating Procedure (SOP), incident reports | `docs/` |

## Quick start

You do not need Java or Maven on the host -- everything runs through Docker.

```bash
# Bring up Kafka, NiFi, the app, Prometheus
docker compose up --build

# Sanity check
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/rso

# Push a telemetry message via the REST API as a producer
curl -s -X POST http://localhost:8080/api/rso \
  -H 'content-type: application/json' \
  -d '{"rsoId":"25544","designator":"ISS","type":"PAYLOAD","orbitClass":"LEO",
       "latitudeDeg":51.6,"longitudeDeg":0.0,"altitudeKm":408.0,
       "lastUpdated":"2026-05-08T00:00:00Z"}'

# Or drop sensor CSV into the NiFi inbox
cp nifi/data/in/sample.csv nifi/data/in/feed-$(date +%s).csv
```

Prometheus is at http://localhost:9090, NiFi at https://localhost:8443
(`admin` / `ChangeThisPasswordPlease`).

## Building / testing locally without host Java

```bash
# Build the jar
docker run --rm -v "$PWD":/w -w /w maven:3.9.9-eclipse-temurin-21 mvn -B package

# Run only unit tests
docker run --rm -v "$PWD":/w -w /w maven:3.9.9-eclipse-temurin-21 mvn -B test

# Run the integration test (uses embedded Kafka)
docker run --rm -v "$PWD":/w -w /w maven:3.9.9-eclipse-temurin-21 mvn -B verify
```

## Operational docs

- [docs/RUNBOOK.md](docs/RUNBOOK.md) -- common failures, how to diagnose and recover
- [docs/SOP.md](docs/SOP.md) -- deploy, rollback, patch procedures
- [docs/incidents/INC-2026-04-001.md](docs/incidents/INC-2026-04-001.md) -- example post-incident report

## Tech stack

- Java 21, Spring Boot 3.3
- Spring Kafka with `ErrorHandlingDeserializer` and `DeadLetterPublishingRecoverer`
- Micrometer + Prometheus
- Logback with Logstash JSON encoder
- JUnit 5, Testcontainers, Spring Kafka test (`@EmbeddedKafka`)
- Apache Kafka 7.7 (KRaft mode), Apache NiFi 2.0
- Distroless `java21-debian12:nonroot` runtime image
