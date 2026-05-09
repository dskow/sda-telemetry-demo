package com.skowronski.sda.telemetry.service;

import com.skowronski.sda.telemetry.domain.OrbitClass;
import com.skowronski.sda.telemetry.domain.Rso;
import com.skowronski.sda.telemetry.domain.RsoType;
import com.skowronski.sda.telemetry.domain.TelemetryUpdate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RsoCatalog {

    private final ConcurrentMap<String, Rso> store = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastObservedBySensor = new ConcurrentHashMap<>();
    private final Counter staleUpdateCounter;
    private final Counter appliedUpdateCounter;

    public RsoCatalog(MeterRegistry meterRegistry) {
        this.staleUpdateCounter = Counter.builder("sda.telemetry.update.stale")
                .description("Telemetry updates older than the last observation for the same RSO+sensor")
                .register(meterRegistry);
        this.appliedUpdateCounter = Counter.builder("sda.telemetry.update.applied")
                .description("Telemetry updates accepted and applied to the catalog")
                .register(meterRegistry);
    }

    public Rso upsert(Rso rso) {
        store.put(rso.rsoId(), rso);
        return rso;
    }

    public Optional<Rso> findById(String rsoId) {
        return Optional.ofNullable(store.get(rsoId));
    }

    public Collection<Rso> findAll() {
        return List.copyOf(store.values());
    }

    public boolean delete(String rsoId) {
        return store.remove(rsoId) != null;
    }

    public boolean applyTelemetry(TelemetryUpdate update) {
        String dedupKey = update.rsoId() + "|" + update.sensorId();
        Instant prior = lastObservedBySensor.get(dedupKey);
        if (prior != null && !update.observedAt().isAfter(prior)) {
            staleUpdateCounter.increment();
            return false;
        }
        lastObservedBySensor.put(dedupKey, update.observedAt());

        store.compute(update.rsoId(), (id, existing) -> {
            if (existing == null) {
                return new Rso(
                        id,
                        id,
                        RsoType.UNKNOWN,
                        OrbitClass.UNKNOWN,
                        update.latitudeDeg(),
                        update.longitudeDeg(),
                        update.altitudeKm(),
                        update.observedAt()
                );
            }
            return existing.withPosition(
                    update.latitudeDeg(),
                    update.longitudeDeg(),
                    update.altitudeKm(),
                    update.observedAt()
            );
        });
        appliedUpdateCounter.increment();
        return true;
    }

    public int size() {
        return store.size();
    }
}
