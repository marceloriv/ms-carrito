package com.ticketti.ms_carrito.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalleCarrito;
    private Integer cantidad;
    private Long idCarritoDeCompras;
    private Long idEntrada;
    private Long eventoId;
    private Long tipoEntradaIdTipo;
    private String tipoEntradaNombre;
    private BigDecimal precioUnitario;
    private Long idReserva;
    private CarritoDeCompras carrito;

}
