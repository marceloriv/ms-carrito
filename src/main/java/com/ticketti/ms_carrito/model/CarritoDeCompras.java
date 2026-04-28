package com.ticketti.ms_carrito.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "CARRITO_DE_COMPRAS")
public class CarritoDeCompras {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCarrito;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUlActualizacion;
    private Long rolUsuarioId;
    private Long usuarioId;
    private EstadoCarrito estadoCarrito;
    private EstadoPago estadoPago;
    private BigDecimal subTotal;
    private BigDecimal montoDonacion;
    private BigDecimal total;
    private Long causaSocialId;
    private Long reservaId;
    private Long idPago;
    private String idempotencyKey;
    private LocalDateTime fechaExpiracionReserva;
    private boolean renovacionUsada;
    @Version
    private Long version;

}
