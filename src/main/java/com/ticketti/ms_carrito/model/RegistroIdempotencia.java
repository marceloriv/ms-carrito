package com.ticketti.ms_carrito.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "REGISTRO_IDEMPOTENCIA")
public class RegistroIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String Key;
    private EstadoIdempotencia estado = EstadoIdempotencia.PENDIENTE;
    private LocalDateTime creacionIdempotencia;
    private LocalDateTime expiracionIdempotencia;

    public enum EstadoIdempotencia {
        PENDIENTE, COMPLETADO, FALLIDO
    }

}
