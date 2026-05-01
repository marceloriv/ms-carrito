package com.ticketti.ms_carrito.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para checkout del carrito.
 * Requiere idempotencyKey para evitar duplicados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutDto {

    @NotNull(message = "El ID de la causa social es obligatorio")
    private Long causaSocialId;

    @NotBlank(message = "Idempotency key es obligatorio")
    @Size(min = 32, max = 255, message = "Idempotency key debe tener entre 32 y 255 caracteres")
    private String idempotencyKey;

    private String token;

    private String requestHash;
}