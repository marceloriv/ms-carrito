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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CARRITO_DE_COMPRAS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarritoDeCompras {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CARRITO")
    private Long idCarrito;

    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "FECHA_ULT_ACTUALIZACION")
    private LocalDateTime fechaUltActualizacion = LocalDateTime.now();

    @Column(name = "ROL_USUARIO_ID_USU_ROL", nullable = false)
    private Long rolUsuarioId;

    @Column(name = "USUARIO_ID")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_CARRITO", length = 20)
    private EstadoCarrito estadoCarrito = EstadoCarrito.CREADO;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_PAGO", length = 20)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Column(name = "SUBTOTAL", precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "MONTO_DONACION", precision = 15, scale = 2)
    private BigDecimal montoDonacion = BigDecimal.ZERO;

    @Column(name = "TOTAL", precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "CAUSA_SOCIAL_ID")
    private Long causaSocialId;

    @Column(name = "RESERVA_ID")
    private Long reservaId;

    @Column(name = "ID_PAGO")
    private Long idPago;

    @Column(name = "IDEMPOTENCY_KEY", length = 255, unique = true)
    private String idempotencyKey;

    @Column(name = "FECHA_EXPIRACION_RESERVA")
    private LocalDateTime fechaExpiracionReserva;

    @Column(name = "RENOVACION_USADA")
    private boolean renovacionUsada = false;

    @Version
    private Long version;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleCarrito> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        fechaUltActualizacion = LocalDateTime.now();
    }

    public void recalcularTotales() {
        this.subtotal = detalles.stream()
                .map(DetalleCarrito::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (this.causaSocialId != null && this.causaSocialId == 0L) {
            this.montoDonacion = BigDecimal.ZERO;
        } else {
            this.montoDonacion = this.subtotal.multiply(new BigDecimal("0.10"));
        }
        this.total = this.subtotal.add(this.montoDonacion);
    }

    public int getTotalEntradas() {
        return detalles.stream()
                .mapToInt(DetalleCarrito::getCantidad)
                .sum();
    }

    public boolean puedeRenovarReserva() {
        return !renovacionUsada && fechaExpiracionReserva != null
                && LocalDateTime.now().isBefore(fechaExpiracionReserva);
    }

    public boolean isRenovacionUsada() {
        return renovacionUsada;
    }

    public void setRenovacionUsada(boolean renovacionUsada) {
        this.renovacionUsada = renovacionUsada;
    }
}
