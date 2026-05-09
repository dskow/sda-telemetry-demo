package com.skowronski.sda.telemetry.kafka;

import com.skowronski.sda.telemetry.domain.TelemetryUpdate;
import com.skowronski.sda.telemetry.service.RsoCatalog;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"rso.telemetry", "rso.telemetry.dlq"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class TelemetryFlowIT {

    @Autowired
    private TelemetryProducer producer;

    @Autowired
    private RsoCatalog catalog;

    @Test
    void producedMessageReachesCatalogViaKafka() {
        TelemetryUpdate update = new TelemetryUpdate(
                "44444", "RADAR-9", 10.0, 20.0, 600.0,
                Instant.parse("2026-05-08T15:00:00Z")
        );

        producer.send(update);

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(catalog.findById("44444")).isPresent());

        assertThat(catalog.findById("44444").get().longitudeDeg()).isEqualTo(20.0);
    }
}
