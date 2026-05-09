# RUNBOOK

Operational guidance for the SDA telemetry service. Use in conjunction with
[SOP.md](SOP.md) for normal-state procedures.

## Service contract

- **Application**: `sda-telemetry`
- **Port**: 8080
- **Health endpoints**: `/actuator/health/liveness`, `/actuator/health/readiness`
- **Metrics**: `/actuator/prometheus`
- **Topics consumed**: `rso.telemetry`
- **Topics produced**: `rso.telemetry.dlq`
- **Service Level Objective (SLO)**: 99.5% of telemetry messages applied
  to the catalog within 5s of broker receipt; max DLQ rate 0.1% of inbound.

## Health-check semantics

| Endpoint | Failure means |
|---|---|
| `/actuator/health/liveness` | Process is wedged. Restart the pod. |
| `/actuator/health/readiness` | Process is alive but not ready (e.g. Kafka unreachable). Stop routing traffic; **do not restart**. |

The readiness probe includes a `kafka` health indicator from
`spring-boot-starter-actuator`. If Kafka is degraded, readiness will flip to DOWN
and the consumer will pause until brokers recover.

## Common alerts

### A1. `dlq-rate-high` -- DLQ depth growing

**Signal**: `rate(kafka_consumer_records_consumed_total{topic="rso.telemetry.dlq"})`
exceeds 1 msg/s for >5 min, or `sda_telemetry_kafka_rejected_total` rising.

**Triage**:

1. Inspect a recent DLQ message:
   ```
   kafka-console-consumer --bootstrap-server kafka:29092 \
     --topic rso.telemetry.dlq --from-beginning --max-messages 1 \
     --property print.headers=true
   ```
2. Check headers for `kafka_dlt-exception-message` and `kafka_dlt-original-topic`.
3. Most common causes:
   - **Schema drift** -- producer started emitting a new field type. Confirm with
     a sample, then either widen the consumer DTO or coordinate a producer rollback.
   - **Malformed JSON** -- almost always upstream. Check NiFi `PutFile - error`
     directory (`nifi/data/error/`) for the offending CSV.
   - **Validation failure** -- e.g. latitude out of range. The producer side has
     a bug; capture an example and file a defect.

### A2. `consumer-lag-high` -- consumer falling behind

**Signal**: `kafka_consumer_lag` for group `sda-telemetry` > 10,000 records.

**Triage**:

1. Confirm the app is up: `curl /actuator/health/readiness`.
2. Check CPU/memory: `kubectl top pod sda-telemetry-*` (or `docker stats sda-telemetry-app`).
3. Look for slow downstream calls in logs (we currently have none, but if/when a
   downstream lookup is added, watch the `sda.telemetry.kafka.processing` timer).
4. Increase parallelism if needed: bump `spring.kafka.listener.concurrency` and
   verify partition count on `rso.telemetry`.

### A3. `stale-update-spike` -- many out-of-order observations

**Signal**: `sda_telemetry_update_stale_total` rises sharply.

**Triage**:

This is *probably* benign -- two sensors covering the same RSO can produce
arrivals out of wall-clock order. Confirm by looking at sensor-id distribution
in logs. If a single sensor is re-emitting old data, ask the upstream owner to
check their replay logic.

### A4. App will not start: `org.apache.kafka.common.errors.TopicAuthorizationException`

The service account lost ACLs on `rso.telemetry` or `rso.telemetry.dlq`. Re-grant
read on the inbound topic and write on the DLQ topic. Do not "fix" by setting
`auto.create.topics.enable=true` in production.

### A5. `OutOfMemoryError` on startup

The default container request is 512 MiB. The JVM is configured with
`-XX:MaxRAMPercentage=75`. If startup OOMs:

1. Check logs for an unusually large message replay (consumer lag was huge).
2. Temporarily lower `max.poll.records` in `application.yml`.
3. If sustained, increase pod memory request to 1 GiB.

## Recovery procedures

### R1. Replay DLQ after fixing the root cause

```
# Read DLQ messages, transform if needed, re-publish to rso.telemetry.
# This is a manual operation -- only run after the upstream defect is fixed.
kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic rso.telemetry.dlq --from-beginning --max-messages 100 \
  > /tmp/dlq.batch
# (review, fix, then re-publish)
kafka-console-producer --bootstrap-server kafka:29092 \
  --topic rso.telemetry --property "parse.key=true" --property "key.separator=|" \
  < /tmp/dlq.batch
```

### R2. Reset a stuck consumer group offset

**Only with maintainer approval.** Resetting offsets re-applies messages and can
cause double-processing of telemetry. The catalog is idempotent on
`(rsoId, sensorId, observedAt)` so this is safe in practice, but record the action.

```
kafka-consumer-groups --bootstrap-server kafka:29092 \
  --group sda-telemetry --reset-offsets --to-earliest --topic rso.telemetry --execute
```

### R3. NiFi flow stopped processing

1. Check NiFi UI -> Bulletin board for component-level errors.
2. Inspect `data/error/` for files routed to the failure relationship.
3. If the `PublishKafka` processor shows broker-side errors, fix Kafka first.
4. Restart only the failed processor (`Stop` -> `Start`), not the whole node.

## Escalation

- **Severity 1** (catalog stale > 30 min, all consumers down): page on-call.
- **Severity 2** (DLQ rate > 1% sustained, but consumers running): file an incident
  ticket and write a follow-up post-incident report under `docs/incidents/`.
- **Severity 3** (single intermittent message in DLQ): no page; address in next
  business-hours triage.
