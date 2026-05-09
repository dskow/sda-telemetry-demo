package com.skowronski.sda.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class SdaTelemetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SdaTelemetryApplication.class, args);
    }
}
