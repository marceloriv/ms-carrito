package com.ticketti.ms_carrito.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ticketti.ms_carrito.client.dto.UsuarioInfoDto;

@FeignClient(name = "ms-usuarios", url = "${ms-usuarios.url:http://localhost:8080}")
public interface UsuarioClient {

    @GetMapping("/api/v1/usuarios/{usuarioId}")
    UsuarioInfoDto buscarUsuario(@PathVariable Long usuarioId);
}
