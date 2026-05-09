package com.ticketti.ms_carrito.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ticketti.exchange";
    public static final String QUEUE_PAGO_APROBADO = "pago.aprobado";
    public static final String QUEUE_COMPRA_REVERTIDA = "compra.revertida";
    public static final String ROUTING_KEY_PAGO = "pago.aprobado";
    public static final String ROUTING_KEY_REVERSION = "compra.revertida";

    public static final String QUEUE_MENSAJERIA    = "mensajeria.queue";
    public static final String QUEUE_DONACIONES    = "donaciones.queue";


    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue queuePagoAprobado() {
        return QueueBuilder.durable(QUEUE_PAGO_APROBADO).build();
    }

    @Bean
    public Queue queueCompraRevertida() {
        return QueueBuilder.durable(QUEUE_COMPRA_REVERTIDA).build();
    }

    @Bean
    public Binding bindingPago(Queue queuePagoAprobado, TopicExchange exchange) {
        return BindingBuilder.bind(queuePagoAprobado).to(exchange).with(ROUTING_KEY_PAGO);
    }

    @Bean
    public Binding bindingReversion(Queue queueCompraRevertida, TopicExchange exchange) {
        return BindingBuilder.bind(queueCompraRevertida).to(exchange).with(ROUTING_KEY_REVERSION);
    }

    @Bean
    public Queue queueMensajeria() {
        return QueueBuilder.durable(QUEUE_MENSAJERIA).build();
    }

    @Bean
    public Queue queueDonaciones() {
        return QueueBuilder.durable(QUEUE_DONACIONES).build();
    }

    @Bean
    public Binding bindingMensajeria(Queue queueMensajeria,
                                     TopicExchange exchange) {
        return BindingBuilder
                .bind(queueMensajeria)
                .to(exchange)
                .with(ROUTING_KEY_PAGO);
    }

    @Bean
    public Binding bindingDonaciones(Queue queueDonaciones,
                                     TopicExchange exchange) {
        return BindingBuilder
                .bind(queueDonaciones)
                .to(exchange)
                .with(ROUTING_KEY_PAGO);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }



}
