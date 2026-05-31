package com.ticketti.ms_carrito.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ticketti.exchange";
    public static final String QUEUE_PAGO_APROBADO = "pago.aprobado";
    public static final String QUEUE_COMPRA_REVERTIDA = "compra.revertida";
    public static final String ROUTING_KEY_PAGO = "pago.aprobado";
    public static final String ROUTING_KEY_REVERSION = "compra.revertida";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
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
    public Binding bindingPago(Queue queuePagoAprobado, DirectExchange exchange) {
        return BindingBuilder.bind(queuePagoAprobado).to(exchange).with(ROUTING_KEY_PAGO);
    }

    @Bean
    public Binding bindingReversion(Queue queueCompraRevertida, DirectExchange exchange) {
        return BindingBuilder.bind(queueCompraRevertida).to(exchange).with(ROUTING_KEY_REVERSION);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
