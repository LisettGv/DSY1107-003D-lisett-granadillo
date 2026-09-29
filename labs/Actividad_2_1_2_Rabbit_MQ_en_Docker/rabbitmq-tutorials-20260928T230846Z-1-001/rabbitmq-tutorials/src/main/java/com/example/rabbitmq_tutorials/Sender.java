package com.example.rabbitmq_tutorials;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Productor (Sender) - Envía mensajes a la cola 'hello'
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Component
public class Sender {

    /**
     * RabbitTemplate es la clase principal de Spring AMQP para enviar mensajes.
     */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Método para enviar un mensaje simple
     */
    public void sendMessage(String message) {
        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            String fullMessage = String.format(
                    "[%s] %s",
                    timestamp,
                    message
            );

            // Enviar el mensaje a la cola 'hello' usando el exchange por defecto
            rabbitTemplate.convertAndSend("hello", fullMessage);
            System.out.println("[v] Mensaje enviado: '" + fullMessage + "'");
        } catch (Exception e) {
            System.err.println("[x] Error enviando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Versión sobrecargada con exchange explícito (para casos avanzados)
     */
    public void sendMessage(String exchange, String routingKey, String message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            System.out.println("[v] Mensaje enviado a exchange='" + exchange
                    + "', routingKey='" + routingKey + "': '" + message + "'");
        } catch (Exception e) {
            System.err.println("[x] Error enviando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
