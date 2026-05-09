package com.skowronski.sda.telemetry.service;

import com.skowronski.sda.telemetry.domain.OrbitClass;
import com.skowronski.sda.telemetry.domain.Rso;
import com.skowronski.sda.telemetry.domain.RsoType;
import com.skowronski.sda.telemetry.domain.TelemetryUpdate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RsoCatalogTest {

    private RsoCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new RsoCatalog(new SimpleMeterRegistry());
    }

    @Test
    void upsertAndFind() {
        Rso rso = new Rso("25544", "ISS (ZARYA)", RsoType.PAYLOAD, OrbitClass.LEO,
                51.6, 0.0, 408.0, Instant.parse("2026-05-08T00:00:00Z"));
        catalog.upsert(rso);

        assertThat(catalog.findById("25544")).contains(rso);
        assertThat(catalog.findAll()).hasSize(1);
    }

    @Test
    void applyTelemetryCreatesNewRsoIfMissing() {
        TelemetryUpdate update = new TelemetryUpdate(
                "99999", "RADAR-7", 12.0, 34.0, 500.0, Instant.parse("2026-05-08T12:00:00Z")
        );
        boolean applied = catalog.applyTelemetry(update);

        assertThat(applied).isTrue();
        assertThat(catalog.findById("99999")).isPresent();
        assertThat(catalog.findById("99999").get().latitudeDeg()).isEqualTo(12.0);
    }

    @Test
    void applyTelemetryRejectsStaleObservation() {
        Instant t1 = Instant.parse("2026-05-08T12:00:00Z");
        Instant t0 = Instant.parse("2026-05-08T11:00:00Z");

        catalog.applyTelemetry(new TelemetryUpdate("X1", "S1", 1.0, 2.0, 300.0, t1));
        boolean appliedStale = catalog.applyTelemetry(new TelemetryUpdate("X1", "S1", 9.9, 9.9, 999.0, t0));

        assertThat(appliedStale).isFalse();
        assertThat(catalog.findById("X1").get().latitudeDeg()).isEqualTo(1.0);
    }

    @Test
    void differentSensorsTrackedIndependently() {
        Instant t1 = Instant.parse("2026-05-08T12:00:00Z");
        Instant t0 = Instant.parse("2026-05-08T11:00:00Z");

        catalog.applyTelemetry(new TelemetryUpdate("X1", "S1", 1.0, 2.0, 300.0, t1));
        boolean appliedFromOtherSensor = catalog.applyTelemetry(
                new TelemetryUpdate("X1", "S2", 5.0, 6.0, 400.0, t0)
        );

        assertThat(appliedFromOtherSensor).isTrue();
        assertThat(catalog.findById("X1").get().latitudeDeg()).isEqualTo(5.0);
    }

    @Test
    void deleteRemovesEntry() {
        catalog.upsert(new Rso("Z1", "Z1", RsoType.DEBRIS, OrbitClass.LEO, 0, 0, 100, Instant.now()));
        assertThat(catalog.delete("Z1")).isTrue();
        assertThat(catalog.delete("Z1")).isFalse();
        assertThat(catalog.findById("Z1")).isEmpty();
    }
}
