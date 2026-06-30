package com.ticketti.ms_carrito.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.config.RabbitMQConfig;
import com.ticketti.ms_carrito.messaging.CompraConfirmadaEvent;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OutboxRelayService {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;
    private static final int BASE_BACKOFF_SECONDS = 5;

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelayService(OutboxEventRepository outboxRepository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publica los eventos pendientes o reintentables del outbox en RabbitMQ.
     * Los eventos se procesan en orden de antigüedad y se marcan como enviados
     * o fallidos según el resultado de la publicación.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publicarEventosPendientes() {
        LocalDateTime ahora = LocalDateTime.now();
        List<OutboxEvent> pendientes = outboxRepository.findByStatus(OutboxEvent.Status.PENDING)
            .stream()
            .filter(event -> listoParaProcesar(event, ahora))
            .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
            .limit(BATCH_SIZE)
            .toList();

        List<OutboxEvent> reintentos = outboxRepository.findByStatus(OutboxEvent.Status.FAILED)
                .stream()
            .filter(event -> listoParaProcesar(event, ahora))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .limit(BATCH_SIZE)
                .toList();

        pendientes = List.copyOf(concatenar(pendientes, reintentos));

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Procesando {} eventos pendientes de outbox", pendientes.size());

        int procesados = 0;
        for (OutboxEvent event : pendientes) {
            try {
                String routingKey = event.getRoutingKey() != null ? event.getRoutingKey() : event.getType();
                try {
                    CompraConfirmadaEvent payload = objectMapper.readValue(event.getPayload(), CompraConfirmadaEvent.class);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
                } catch (Exception deserEx) {
                    // Fallback: enviar el payload original (JSON string) para compatibilidad
                    log.warn("No se pudo deserializar payload a CompraConfirmadaEvent (id {}): {}. Enviando payload bruto.", event.getId(), deserEx.getMessage());
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event.getPayload());
                }

                event.setStatus(OutboxEvent.Status.SENT);
                event.setSentAt(LocalDateTime.now());
                outboxRepository.save(event);

                procesados++;
                log.debug("Evento outbox {} publicado con routing key {}", event.getId(), routingKey);
            } catch (org.springframework.amqp.AmqpException | DataAccessException e) {
                log.error("Error publicando evento outbox {}: {}", event.getId(), e.getMessage());
                event.incrementarRetry();
                event.setStatus(OutboxEvent.Status.FAILED);
                outboxRepository.save(event);

                if (!event.puedeReintentar(MAX_RETRIES)) {
                    log.warn("Evento outbox {} agotó reintentos y queda en FAILED", event.getId());
                }
            }
        }

        log.info("Outbox relay completado. Procesados: {}", procesados);
    }

    /**
     * Determina si un evento está listo para enviarse en este ciclo del relay.
     *
     * @param event evento a evaluar.
     * @param ahora instante actual.
     * @return {@code true} si el evento puede enviarse; en caso contrario, {@code false}.
     */
    private boolean listoParaProcesar(OutboxEvent event, LocalDateTime ahora) {
        if (event.getStatus() == OutboxEvent.Status.PENDING) {
            return true;
        }

        if (event.getStatus() != OutboxEvent.Status.FAILED) {
            return false;
        }

        if (!event.puedeReintentar(MAX_RETRIES)) {
            return false;
        }

        long backoffSeconds = (long) Math.min(Math.pow(2, event.getRetryCount()) * BASE_BACKOFF_SECONDS, 300);
        return event.getCreatedAt().plusSeconds(backoffSeconds).isBefore(ahora);
    }

    /**
     * Combina dos listas de eventos en una sola lista mutable.
     *
     * @param primero primera lista de eventos.
     * @param segundo segunda lista de eventos.
     * @return lista combinada con el contenido de ambas listas.
     */
    private List<OutboxEvent> concatenar(List<OutboxEvent> primero, List<OutboxEvent> segundo) {
        List<OutboxEvent> combinados = new java.util.ArrayList<>(primero);
        combinados.addAll(segundo);
        return combinados;
    }
}
