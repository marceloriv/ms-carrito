package com.ticketti.ms_carrito.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OutboxEvent - Evento para publicación asíncrona (patrón Outbox). Almacena
 * payload y metadatos para publicación eventual fuera del flujo transaccional
 * principal.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status"),
    @Index(name = "idx_outbox_created", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;  // pedidoId

    @Column(nullable = false, length = 100)
    private String type;  // PAGO_CONFIRMADO, COMPRA_REVERTIDA

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;  // JSON

    @Column(length = 100)
    private String routingKey;  // pago.aprobado, compra.revertida

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime sentAt;

    private Integer retryCount = 0;

    public enum Status {
        PENDING, SENT, FAILED
    }

    public void marcarEnviado() {
        this.status = Status.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void incrementarRetry() {
        this.retryCount++;
    }

    public boolean puedeReintentar(int maxRetries) {
        return retryCount < maxRetries && status != Status.SENT;
    }
}
