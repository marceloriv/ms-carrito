package com.ticketti.ms_carrito.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class EstadisticasRequestDto {
    @NotEmpty(message = "Se requiere al menos un ID de evento")
    private List<Long> eventoIds;
}
