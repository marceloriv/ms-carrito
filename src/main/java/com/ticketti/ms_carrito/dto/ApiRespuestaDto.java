package com.ticketti.ms_carrito.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiRespuestaDto<T> {
    private boolean exito;
    private String mensaje;
    private T data;

    public static <T> ApiRespuestaDto<T> exito(String mensaje, T data) {
        return ApiRespuestaDto.<T>builder()
                .exito(true)
                .mensaje(mensaje)
                .data(data)
                .build();
    }

    public static <T> ApiRespuestaDto<T> exito(String mensaje) {
        return exito(mensaje, null);
    }

    public static <T> ApiRespuestaDto<T> error(String mensaje, T data) {
        return ApiRespuestaDto.<T>builder()
                .exito(false)
                .mensaje(mensaje)
                .data(data)
                .build();
    }

    public static <T> ApiRespuestaDto<T> error(String mensaje) {
        return error(mensaje, null);
    }
}