package com.ticketti.ms_carrito.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
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

    private Long idReserva;
    private LocalDateTime fechaReserva;
    private String nombreRol;
    private String descripcion;
    private Long usuarioIdUsu;
    private Long rolUsuarioIdUsuRol;
    private Long carritoDeComprasIdCarrito;
    private Long eventoId;
    private Integer cantidadEntradas;
    private LocalDateTime fechaExpiracion;

}
