package com.ticketti.ms_carrito.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "DETALLE_CARRITO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DETALLE_CARRITO")
    private Long idDetalleCarrito;

    @Column(name = "CANTIDAD", nullable = false)
    private Integer cantidad;

    @Column(name = "ID_CARRITO_DE_COMPRAS", nullable = false)
    private Long idCarritoDeCompras;

    @Column(name = "ID_ENTRADA")
    private Long idEntrada;

    @Column(name = "EVENTO_ID", nullable = false)
    private Long eventoId;

    @Column(name = "TIPO_ENTRADA_ID_TIPO")
    private Long tipoEntradaIdTipo;

    @Column(name = "TIPO_ENTRADA_NOMBRE", length = 50)
    private String tipoEntradaNombre;

    @Column(name = "PRECIO_UNITARIO", precision = 15, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "ID_RESERVA")
    private Long idReserva;

    @ManyToOne
    @JoinColumn(name = "ID_CARRITO_DE_COMPRAS", referencedColumnName = "ID_CARRITO", insertable = false, updatable = false)
    @JsonIgnore
    private CarritoDeCompras carrito;

    public BigDecimal getSubtotal() {
        if (precioUnitario == null || cantidad == null) {
            return BigDecimal.ZERO;
        }
        return precioUnitario.multiply(new BigDecimal(cantidad));
    }
}
