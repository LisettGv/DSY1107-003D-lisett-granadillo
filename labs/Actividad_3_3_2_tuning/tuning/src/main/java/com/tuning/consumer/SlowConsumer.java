package com.tuning.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class SlowConsumer {

    @KafkaListener(topics = "sensor-topic", groupId = "analytics-group")
    public void listen(String message) {
        // Simular procesamiento pesado (10ms por mensaje)
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}