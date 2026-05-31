package com.ticketti.ms_carrito.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.messaging.CompraConfirmadaEvent;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OutboxRelayService {

    private static final String EXCHANGE_NAME = "ticketti.exchange";
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelayService(OutboxEventRepository outboxRepository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEvent> pendientes = outboxRepository.findByStatus(OutboxEvent.Status.PENDING)
                .stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .limit(BATCH_SIZE)
                .toList();

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
                    rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, payload);
                } catch (Exception deserEx) {
                    // Fallback: enviar el payload original (JSON string) para compatibilidad
                    log.warn("No se pudo deserializar payload a CompraConfirmadaEvent (id {}): {}. Enviando payload bruto.", event.getId(), deserEx.getMessage());
                    rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, event.getPayload());
                }

                event.setStatus(OutboxEvent.Status.SENT);
                event.setSentAt(LocalDateTime.now());
                outboxRepository.save(event);

                procesados++;
                log.debug("Evento outbox {} publicado con routing key {}", event.getId(), routingKey);
            } catch (Exception e) {
                log.error("Error publicando evento outbox {}: {}", event.getId(), e.getMessage());
                event.setStatus(OutboxEvent.Status.FAILED);
                outboxRepository.save(event);
            }
        }

        log.info("Outbox relay completado. Procesados: {}", procesados);
    }
}
