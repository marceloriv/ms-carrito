package com.ticketti.ms_carrito.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PAGO")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPago;
    private BigDecimal montoTotal;
    private LocalDateTime fechaPago;
    private String metodoPago;
    private Long idMensajeria;
    private Long reservaIdReserva;
    private Long carritoIdCarrito;
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;
    private String idempotenciaKey;
    private String tokenPasarela;

    

}
