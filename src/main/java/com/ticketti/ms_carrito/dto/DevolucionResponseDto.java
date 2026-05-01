package com.ticketti.ms_carrito.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevolucionResponseDto {
    private Long pedidoId;
    private String estadoDevolucion;
    private BigDecimal montoTotal;
    private BigDecimal montoDevolucion;
    private BigDecimal montoDonacionNoReembolsable;
    private String mensaje;
    private LocalDateTime fechaProcesamiento;
}