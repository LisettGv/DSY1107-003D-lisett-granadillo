package com.example.rabbitmq_tutorials;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ
 * 
 * Basada en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Define la cola "hello"
     * 
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     * 
     * durable = false: la cola no sobrevive a un reinicio de RabbitMQ.
     */
    @Bean
    public Queue helloQueue() {
        return new Queue("hello", false);
    }
}