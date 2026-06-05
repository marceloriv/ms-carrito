package com.ticketti.ms_carrito.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pedido - Entidad principal del dominio (reemplaza `CarritoDeCompras`).
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
    private EstadoPedido estadoPedido = EstadoPedido.CREADO;

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
            estadoPedido = EstadoPedido.CREADO;
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
     * Método de negocio: Recalcula totales basado en items
     */
    public void recalcularTotales() {
        this.subtotal = items.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.montoDonacion = this.subtotal.multiply(new BigDecimal("0.10"));
        this.total = this.subtotal.add(this.montoDonacion);
    }

    /**
     * Método de negocio: Cuenta el total de entradas
     */
    public int getTotalEntradas() {
        return items.stream()
                .mapToInt(ItemPedido::getCantidad)
                .sum();
    }

    /**
     * Método de negocio: Valida máximo 4 entradas por evento
     */
    public boolean validarLimiteEntradas(Long eventoId, int cantidadNueva) {
        int entradasActuales = items.stream()
                .filter(item -> item.getEventoId().equals(eventoId))
                .mapToInt(ItemPedido::getCantidad)
                .sum();
        return (entradasActuales + cantidadNueva) <= 4;
    }

    /**
     * Transición de estado: CREADO → RESERVADO
     */
    public void reservar() {
        this.estadoPedido.validarTransicion(EstadoPedido.RESERVADO);
        this.estadoPedido = EstadoPedido.RESERVADO;
    }

    /**
     * Transición de estado: RESERVADO → CANCELADO
     */
    public void cancelar() {
        this.estadoPedido.validarTransicion(EstadoPedido.CANCELADO);
        this.estadoPedido = EstadoPedido.CANCELADO;
    }

    /**
     * Transición de estado: RESERVADO → REEMBOLSADO
     */
    public void reembolsar() {
        this.estadoPedido.validarTransicion(EstadoPedido.REEMBOLSADO);
        this.estadoPago.validarTransicion(EstadoPago.REEMBOLSADO);
        this.estadoPedido = EstadoPedido.REEMBOLSADO;
        this.estadoPago = EstadoPago.REEMBOLSADO;
    }

    /**
     * Transición de estado: PENDIENTE → PAGADO
     */
    public void marcarPagado() {
        this.estadoPago.validarTransicion(EstadoPago.PAGADO);
        this.estadoPago = EstadoPago.PAGADO;
    }

    /**
     * Transición de estado: PENDIENTE → FALLIDO
     */
    public void marcarPagoFallido() {
        this.estadoPago.validarTransicion(EstadoPago.FALLIDO);
        this.estadoPago = EstadoPago.FALLIDO;
    }

    /**
     * Verifica si la reserva puede renovarse
     */
    public boolean puedeRenovarReserva() {
        return fechaExpiracionReserva != null
                && LocalDateTime.now().isBefore(fechaExpiracionReserva)
                && estadoPedido == EstadoPedido.RESERVADO;
    }

    /**
     * Añade un item al pedido
     */
    public void agregarItem(ItemPedido item) {
        items.add(item);
        item.setPedido(this);
        recalcularTotales();
    }

    /**
     * Elimina un item del pedido
     */
    public void removerItem(ItemPedido item) {
        items.remove(item);
        item.setPedido(null);
        recalcularTotales();
    }
}
