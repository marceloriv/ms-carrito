package com.ticketti.ms_carrito.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevolucionRequestDto {
    @NotNull(message = "El ID del pedido es obligatorio")
    private Long pedidoId;

    @Size(max = 500, message = "La razón no puede exceder 500 caracteres")
    private String razon;
}