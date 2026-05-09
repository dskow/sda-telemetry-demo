package com.skowronski.sda.telemetry.domain;

import java.time.Instant;

public record Rso(
        String rsoId,
        String designator,
        RsoType type,
        OrbitClass orbitClass,
        double latitudeDeg,
        double longitudeDeg,
        double altitudeKm,
        Instant lastUpdated
) {
    public Rso withPosition(double lat, double lon, double altKm, Instant when) {
        return new Rso(rsoId, designator, type, orbitClass, lat, lon, altKm, when);
    }
}
