package com.ticketti.ms_carrito.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido - Entidad principal del dominio (reemplaza CarritoDeCompras).
 * Aligned with ERS RF-3, RF-6.
 */
@Entity
@Table(name = "PEDIDO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PEDIDO")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_PEDIDO", length = 20, nullable = false)
    private EstadoPedido estadoPedido = EstadoPedido.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_PAGO", length = 20, nullable = false)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Column(name = "SUBTOTAL", precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "MONTO_DONACION", precision = 15, scale = 2)
    private BigDecimal montoDonacion = BigDecimal.ZERO;

    @Column(name = "TOTAL", precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "CAUSA_SOCIAL_ID")
    private Long causaSocialId;

    @Column(name = "IDEMPOTENCY_KEY", length = 255, unique = true)
    private String idempotencyKey;

    @Column(name = "RESERVA_ID")
    private Long reservaId;

    @Column(name = "FECHA_EXPIRACION_RESERVA")
    private LocalDateTime fechaExpiracionReserva;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (estadoPedido == null) {
            estadoPedido = EstadoPedido.CREATED;
        }
        if (estadoPago == null) {
            estadoPago = EstadoPago.PENDIENTE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Business method: Recalculate totals based on items
     */
    public void recalcularTotales() {
        this.subtotal = items.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.montoDonacion = this.subtotal.multiply(new BigDecimal("0.10"));
        this.total = this.subtotal.add(this.montoDonacion);
    }

    /**
     * Business method: Count total entries
     */
    public int getTotalEntradas() {
        return items.stream()
                .mapToInt(ItemPedido::getCantidad)
                .sum();
    }

    /**
     * Business method: Validate max 4 entries per event
     */
    public boolean validarLimiteEntradas(Long eventoId, int cantidadNueva) {
        int entradasActuales = items.stream()
                .filter(item -> item.getEventoId().equals(eventoId))
                .mapToInt(ItemPedido::getCantidad)
                .sum();
        return (entradasActuales + cantidadNueva) <= 4;
    }

    /**
     * State transition: CREATED → RESERVED
     */
    public void reservar() {
        this.estadoPedido.validarTransicion(EstadoPedido.RESERVED);
        this.estadoPedido = EstadoPedido.RESERVED;
    }

    /**
     * State transition: RESERVED → CANCELLED
     */
    public void cancelar() {
        this.estadoPedido.validarTransicion(EstadoPedido.CANCELLED);
        this.estadoPedido = EstadoPedido.CANCELLED;
    }

    /**
     * State transition: PAID → REFUNDED
     */
    public void reembolsar() {
        this.estadoPedido.validarTransicion(EstadoPedido.REFUNDED);
        this.estadoPago.validarTransicion(EstadoPago.REEMBOLSADO);
        this.estadoPedido = EstadoPedido.REFUNDED;
        this.estadoPago = EstadoPago.REEMBOLSADO;
    }

    /**
     * Payment state: PENDING → PAID
     */
    public void marcarPagado() {
        this.estadoPago.validarTransicion(EstadoPago.PAGADO);
        this.estadoPago = EstadoPago.PAGADO;
    }

    /**
     * Payment state: PENDING → FAILED
     */
    public void marcarPagoFallido() {
        this.estadoPago.validarTransicion(EstadoPago.FALLIDO);
        this.estadoPago = EstadoPago.FALLIDO;
    }

    /**
     * Check if reservation can be renewed
     */
    public boolean puedeRenovarReserva() {
        return fechaExpiracionReserva != null 
                && LocalDateTime.now().isAfter(fechaExpiracionReserva)
                && estadoPedido == EstadoPedido.RESERVED;
    }

    /**
     * Add item to order
     */
    public void agregarItem(ItemPedido item) {
        items.add(item);
        item.setPedido(this);
        recalcularTotales();
    }

    /**
     * Remove item from order
     */
    public void removerItem(ItemPedido item) {
        items.remove(item);
        item.setPedido(null);
        recalcularTotales();
    }
}
