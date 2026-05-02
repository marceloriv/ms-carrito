package com.ticketti.ms_carrito.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "RESERVA")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_RESERVA")
    private Long idReserva;

    @Column(name = "FECHA_RESERVA", nullable = false)
    private LocalDateTime fechaReserva = LocalDateTime.now();

    @Column(name = "NOMBRE_ROL", length = 25)
    private String nombreRol;

    @Column(name = "DESCRIPCION", length = 200)
    private String descripcion;

    @Column(name = "USUARIO_ID_USU", nullable = false)
    private Long usuarioIdUsu;

    @Column(name = "ROL_USUARIO_ID_USU_ROL", nullable = false)
    private Long rolUsuarioIdUsuRol;

    @Column(name = "CARRITO_DE_COMPRAS_ID_CARRITO", nullable = false)
    private Long carritoDeComprasIdCarrito;

    @Column(name = "EVENTO_ID")
    private Long eventoId;

    @Column(name = "CANTIDAD_ENTRADAS")
    private Integer cantidadEntradas;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_RESERVA", length = 20)
    private EstadoReserva estadoReserva = EstadoReserva.RESERVA_INICIADA;

    @Column(name = "FECHA_EXPIRACION")
    private LocalDateTime fechaExpiracion;

    public enum EstadoReserva {
        RESERVA_INICIADA, RESERVA_CONFIRMADA, RESERVA_PENDIENTE,
        RESERVA_FALLIDA, RESERVA_CANCELADA, RESERVA_FINALIZADA
    }
}
