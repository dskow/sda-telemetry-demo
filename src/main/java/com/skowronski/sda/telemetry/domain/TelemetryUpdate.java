package com.skowronski.sda.telemetry.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TelemetryUpdate(
        @NotBlank String rsoId,
        @NotBlank String sensorId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitudeDeg,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitudeDeg,
        @NotNull @DecimalMin("0.0") Double altitudeKm,
        @NotNull Instant observedAt
) {
    @JsonCreator
    public TelemetryUpdate(
            @JsonProperty("rsoId") String rsoId,
            @JsonProperty("sensorId") String sensorId,
            @JsonProperty("latitudeDeg") Double latitudeDeg,
            @JsonProperty("longitudeDeg") Double longitudeDeg,
            @JsonProperty("altitudeKm") Double altitudeKm,
            @JsonProperty("observedAt") Instant observedAt
    ) {
        this.rsoId = rsoId;
        this.sensorId = sensorId;
        this.latitudeDeg = latitudeDeg;
        this.longitudeDeg = longitudeDeg;
        this.altitudeKm = altitudeKm;
        this.observedAt = observedAt;
    }
}
