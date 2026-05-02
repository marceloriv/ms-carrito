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
@Table(name = "EVENTO_SALIENTE")
public class EventoSaliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String idAgregado;
    private String tipo;
    private String carga;
    private EstadoSaliente estado = EstadoSaliente.PENDIENTE;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaEnvio;

    public enum EstadoSaliente {
        PENDIENTE, ENVIADO, FALLIDO
    }

}
