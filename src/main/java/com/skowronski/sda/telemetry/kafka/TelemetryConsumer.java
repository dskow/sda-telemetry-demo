package com.skowronski.sda.telemetry.kafka;

import com.skowronski.sda.telemetry.domain.TelemetryUpdate;
import com.skowronski.sda.telemetry.service.RsoCatalog;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);

    private final RsoCatalog catalog;
    private final Counter receivedCounter;
    private final Counter rejectedCounter;
    private final Timer processingTimer;

    public TelemetryConsumer(RsoCatalog catalog, MeterRegistry meterRegistry) {
        this.catalog = catalog;
        this.receivedCounter = Counter.builder("sda.telemetry.kafka.received")
                .description("Total telemetry messages received from Kafka")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("sda.telemetry.kafka.rejected")
                .description("Telemetry messages rejected for invalid payload")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("sda.telemetry.kafka.processing")
                .description("Time spent processing a telemetry message end-to-end")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${sda.kafka.topics.telemetry}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(
            @Payload TelemetryUpdate update,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        receivedCounter.increment();
        MDC.put("messageKey", key == null ? "" : key);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("rsoId", update == null ? "" : update.rsoId());
        try {
            processingTimer.record(() -> {
                if (update == null || update.rsoId() == null || update.rsoId().isBlank()) {
                    rejectedCounter.increment();
                    log.warn("Rejected telemetry: missing rsoId");
                    throw new IllegalArgumentException("rsoId is required");
                }
                boolean applied = catalog.applyTelemetry(update);
                if (applied) {
                    log.info("Applied telemetry update");
                } else {
                    log.info("Stale telemetry update ignored");
                }
            });
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
