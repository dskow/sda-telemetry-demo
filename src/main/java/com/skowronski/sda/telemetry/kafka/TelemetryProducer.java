package com.skowronski.sda.telemetry.kafka;

import com.skowronski.sda.telemetry.domain.TelemetryUpdate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TelemetryProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public TelemetryProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${sda.kafka.topics.telemetry}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<?> send(TelemetryUpdate update) {
        return kafkaTemplate.send(topic, update.rsoId(), update);
    }
}
