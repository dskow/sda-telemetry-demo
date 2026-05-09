# Standard Operating Procedure

Normal-state operations for the SDA telemetry service. For abnormal-state
recovery, see [RUNBOOK.md](RUNBOOK.md).

## SOP-01: Deploy a new version

**Frequency**: as needed; targeted no more than weekly in production.

1. Tag the release: `git tag -s v0.X.Y -m "release v0.X.Y"` (signed tag required).
2. Build and push image:
   ```
   docker build -t registry.local/sda-telemetry:v0.X.Y .
   docker push registry.local/sda-telemetry:v0.X.Y
   ```
3. In the deployment environment, update the image tag:
   ```
   kubectl -n sda set image deployment/sda-telemetry app=registry.local/sda-telemetry:v0.X.Y
   ```
4. Watch rollout: `kubectl -n sda rollout status deployment/sda-telemetry --timeout=5m`.
5. Smoke test: `curl https://<host>/actuator/health/readiness`.
6. Confirm consumer lag returns to baseline within 10 minutes
   (Prometheus: `kafka_consumer_lag{group="sda-telemetry"}`).
7. Record the deploy in the change log with the git tag and the prior tag.

## SOP-02: Roll back

1. Identify the prior known-good tag.
2. `kubectl -n sda rollout undo deployment/sda-telemetry`
   (or `set image` to the prior tag explicitly).
3. Verify with the same smoke test as SOP-01 step 5.
4. File an incident report under `docs/incidents/` describing the trigger.

## SOP-03: Apply a security patch

A security patch is any change scoped to closing a Common Vulnerabilities and
Exposures (CVE) finding -- base-image bump, dependency bump, or compensating
control. No new functionality permitted in the same change.

1. Bump the affected dependency in `pom.xml` (or the base-image tag in `Dockerfile`).
2. Run `mvn dependency:tree -Dverbose` and confirm the transitive resolution.
3. Run the full test suite: `mvn -B verify`.
4. Open a PR titled `security: bump <component> to <version> (CVE-YYYY-NNNNN)`.
5. Reference the CVE link and CVSS score in the PR body.
6. After merge, follow SOP-01 and tag the release as a patch (`v0.X.Y` -> `v0.X.(Y+1)`).

## SOP-04: Add or modify a Kafka topic

1. Topics are managed via `kafka-init` in `docker-compose.yml` for local;
   in production, topics are managed by infrastructure-as-code (out of scope here).
2. Never call `kafka-topics --create` ad hoc against production.
3. When introducing a new consumer topic in code, update:
   - `application.yml` -- `sda.kafka.topics.<name>`
   - `RUNBOOK.md` -- consumed/produced lists
   - SOP-05 retention table below

## SOP-05: Topic retention reference

| Topic | Partitions | Retention | Cleanup |
|---|---|---|---|
| `rso.telemetry` | 3 | 7 days | delete |
| `rso.telemetry.dlq` | 1 | 30 days | delete |

DLQ retention is intentionally longer so we can investigate stale failures.

## SOP-06: Routine log review

Run weekly during business hours. Look for:
- Repeated `Rejected telemetry: missing rsoId` -- upstream producer regression.
- Repeated `Stale telemetry update ignored` from a single sensor -- replay loop.
- Any `ERROR` not previously seen -- file a ticket and add to RUNBOOK if recurrent.

## SOP-07: Patching the base image

The runtime base image is `gcr.io/distroless/java21-debian12:nonroot`. Distroless
images are rebuilt continuously by upstream. Re-pull and rebuild monthly even
without a code change, and rerun SOP-01.

## Change-management notes

- Every commit to `main` requires a signed-off-by trailer and a review approval.
- Direct push to `main` is disabled.
- Production deploys require a recorded ticket reference in the deploy command's
  change log line.
