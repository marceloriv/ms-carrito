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

    /**
     * Declara el exchange principal usado para publicar eventos del carrito.
     *
     * @return exchange directo compartido entre los microservicios.
     */
    @Bean
    public DirectExchange exchangePrincipal() {
        return new DirectExchange(EXCHANGE);
    }

    /**
     * Declara la cola para eventos de pago aprobado.
     *
     * @return cola durable de pagos aprobados.
     */
    @Bean
    public Queue queuePagoAprobado() {
        return QueueBuilder.durable(QUEUE_PAGO_APROBADO).build();
    }

    /**
     * Declara la cola para eventos de reversión de compra.
     *
     * @return cola durable de reversión.
     */
    @Bean
    public Queue queueCompraRevertida() {
        return QueueBuilder.durable(QUEUE_COMPRA_REVERTIDA).build();
    }

    /**
     * Vincula la cola de pago aprobado con su routing key.
     *
     * @param queuePagoAprobado cola destino.
     * @param exchange exchange compartido.
     * @return binding configurado para pagos aprobados.
     */
    @Bean
    public Binding bindingPago(Queue queuePagoAprobado, DirectExchange exchange) {
        return BindingBuilder.bind(queuePagoAprobado).to(exchange).with(ROUTING_KEY_PAGO);
    }

    /**
     * Vincula la cola de reversión con su routing key.
     *
     * @param queueCompraRevertida cola destino.
     * @param exchange exchange compartido.
     * @return binding configurado para reversiones.
     */
    @Bean
    public Binding bindingReversion(Queue queueCompraRevertida, DirectExchange exchange) {
        return BindingBuilder.bind(queueCompraRevertida).to(exchange).with(ROUTING_KEY_REVERSION);
    }

    /**
     * Crea la plantilla de RabbitMQ usando conversión JSON.
     *
     * @param connectionFactory fábrica de conexiones AMQP.
     * @return plantilla preparada para serializar mensajes como JSON.
     */
    @Bean
    @SuppressWarnings("removal")
    public RabbitTemplate plantillaRabbit(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
