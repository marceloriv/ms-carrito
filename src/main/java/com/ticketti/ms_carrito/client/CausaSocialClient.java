package com.ticketti.ms_carrito.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ticketti.ms_carrito.client.dto.CausaSocialInfoDto;

@FeignClient(name = "ms-donaciones", url = "${ms-donaciones.url:http://localhost:8084}")
public interface CausaSocialClient {

    @GetMapping("/api/v1/causas/{causaId}")
    CausaSocialInfoDto buscarCausa(@PathVariable Long causaId);
}
