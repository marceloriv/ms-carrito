package com.ticketti.ms_carrito.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NonceRecord - Entidad para protección anti-replay de webhooks de pago.
 * Almacena cada nonce utilizado para prevenir procesamiento duplicado.
 */
@Entity
@Table(name = "nonce_records", indexes = {
    @Index(name = "idx_nonce_used_at", columnList = "usedAt"),
    @Index(name = "idx_nonce_expires_at", columnList = "expiresAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NonceRecord {

    @Id
    @Column(name = "nonce", length = 255)
    private String nonce;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "carrito_id")
    private Long carritoId;

    public boolean estaExpirado() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
