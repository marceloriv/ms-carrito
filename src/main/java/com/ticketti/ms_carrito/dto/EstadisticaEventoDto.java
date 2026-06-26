package com.ticketti.ms_carrito.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticaEventoDto {
    private Long eventoId;
    private Long entradasVendidas;
    private BigDecimal ingresos;
    private Long entradasReembolsadas;
}
