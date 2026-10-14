package com.example.rabbitmq_cluster_dockcomp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RabbitMQAdvancedApplication {
    public static void main(String[] args) {
        SpringApplication.run(RabbitMQAdvancedApplication.class, args);
    }
}