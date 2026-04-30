package com.ticketti.ms_carrito.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ItemPedido - Entidad de línea de pedido (reemplaza DetalleCarrito).
 */
@Entity
@Table(name = "ITEM_PEDIDO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PEDIDO_ID", nullable = false)
    private Pedido pedido;

    @Column(name = "EVENTO_ID", nullable = false)
    private Long eventoId;

    @Column(name = "TIPO_ENTRADA", length = 50, nullable = false)
    private String tipoEntrada;

    @Column(name = "CANTIDAD", nullable = false)
    private Integer cantidad;

    @Column(name = "PRECIO_UNITARIO", precision = 15, scale = 2, nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "RESERVA_ID")
    private Long reservaId;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
        if (precioUnitario == null || precioUnitario.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio unitario no puede ser negativo");
        }
    }

    /**
     * Calculate subtotal for this line item
     */
    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(new BigDecimal(cantidad));
    }

    /**
     * Factory method to create valid item
     */
    public static ItemPedido crear(Long eventoId, String tipoEntrada, 
                                   Integer cantidad, BigDecimal precioUnitario) {
        if (cantidad > 4) {
            throw new IllegalArgumentException("Máximo 4 entradas por evento");
        }
        ItemPedido item = new ItemPedido();
        item.setEventoId(eventoId);
        item.setTipoEntrada(tipoEntrada);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioUnitario);
        return item;
    }
}
