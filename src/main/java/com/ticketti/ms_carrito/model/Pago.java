package com.ticketti.ms_carrito.model;

import java.math.BigDecimal;
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
@Table(name = "PAGO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PAGO")
    private Long idPago;

    @Column(name = "MONTO_TOTAL", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @Column(name = "FECHA_PAGO", nullable = false)
    private LocalDateTime fechaPago = LocalDateTime.now();

    @Column(name = "METODO_PAGO", length = 50)
    private String metodoPago;

    @Column(name = "ID_MENSAJERIA")
    private Long idMensajeria;

    @Column(name = "RESERVA_ID_RESERVA", nullable = false)
    private Long reservaIdReserva;

    @Column(name = "CARRITO_ID_CARRITO")
    private Long carritoIdCarrito;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_PAGO", length = 20)
    private EstadoPago estadoPago = EstadoPago.PAGO_PENDIENTE;

    @Column(name = "IDEMPOTENCY_KEY", length = 255)
    private String idempotencyKey;

    @Column(name = "TOKEN_PASARELA", length = 500)
    private String tokenPasarela;

    public enum EstadoPago {
        PAGO_PENDIENTE, PAGO_APROBADO, PAGO_RECHAZADO
    }
}
