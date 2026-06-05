package com.ticketti.ms_carrito.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPagoDto {
    @NotNull(message = "El ID del pedido es obligatorio")
    private Long pedidoId;

    @NotBlank(message = "El estado es obligatorio")
    private String estado;

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "El timestamp es obligatorio")
    private String timestamp;

    @NotBlank(message = "El nonce es obligatorio")
    private String nonce;
}
