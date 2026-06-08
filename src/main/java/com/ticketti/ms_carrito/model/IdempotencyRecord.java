package com.ticketti.ms_carrito.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IdempotencyRecord - Entidad para garantizar idempotencia global. Almacena
 * estado y snapshot de la respuesta para operaciones idénticas.
 */
@Entity
@Table(name = "idempotency_records", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_idempotency_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String key;  // Idempotency key (PK)

    @Column(length = 64)
    private String requestHash;  // SHA-256 del request

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;  // JSON del response exitoso

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime expiresAt;

    private LocalDateTime processedAt;

    public enum Status {
        PENDING, COMPLETED, FAILED
    }

    public boolean isProcesado() {
        return status == Status.COMPLETED;
    }

    public void marcarCompletado(String response) {
        this.status = Status.COMPLETED;
        this.responseSnapshot = response;
        this.processedAt = LocalDateTime.now();
    }

    public void marcarFallido() {
        this.status = Status.FAILED;
        this.processedAt = LocalDateTime.now();
    }
}
