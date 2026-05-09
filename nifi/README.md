# NiFi flow: CSV sensor feed -> Kafka

This directory contains the manually-imported flow definition and a sample input file
for the SDA telemetry ingest pipeline.

## What the flow does

```
GetFile (data/in)
   -> SplitRecord (CSVReader / JsonRecordSetWriter)
   -> UpdateAttribute (set kafka.key = ${rsoId})
   -> PublishKafka_2_6 (rso.telemetry, key = ${kafka.key})
   -> PutFile (data/out, on success) | PutFile (data/error, on failure)
```

Producer settings to set on `PublishKafka`:
- `Kafka Brokers`: `kafka:29092`
- `Topic Name`: `rso.telemetry`
- `Use Transactions`: `false`
- `Delivery Guarantee`: `Guarantee Replicated Delivery`
- `Kafka Key`: `${kafka.key}`
- `Use Record Reader / Writer`: `true` (CSVReader -> JsonRecordSetWriter)

## Flow definition

`flow.json` contains the exported flow definition. Import via NiFi UI:

1. Open https://localhost:8443/nifi (accept self-signed cert).
2. Log in with `admin` / `ChangeThisPasswordPlease` (set in `docker-compose.yml`).
3. Drag a Process Group onto the canvas, choose "Upload from File", select `flow.json`.
4. Start the group.

## Sample input

`data/in/sample.csv`:

```
rsoId,sensorId,latitudeDeg,longitudeDeg,altitudeKm,observedAt
25544,RADAR-7,51.6,0.0,408.0,2026-05-08T12:00:00Z
44444,RADAR-9,10.0,20.0,600.0,2026-05-08T12:01:00Z
```

NiFi watches `/data/in` (mounted from `./nifi/data/in`); drop a CSV file there to trigger the flow.

## Why NiFi here

In a real SDA system, telemetry arrives over heterogeneous transports (file drops from
sensor sites, HTTPS push from external partners, syslog feeds). NiFi normalizes those
sources, tags them with sensor metadata, and produces canonical JSON onto Kafka -- where
the rest of the pipeline can rely on a stable schema.
