package com.ticketti.ms_carrito.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ticketti.ms_carrito.client.dto.EventoInfoDto;
import com.ticketti.ms_carrito.dto.ReservaRequestDto;

@FeignClient(name = "ms-eventos")
public interface EventoClient {

    @GetMapping("/api/v0/Eventos/buscarEvento/{eventoId}")
    EventoInfoDto buscarEvento(@PathVariable Integer eventoId);

    @PutMapping("/api/v0/Eventos/actualizarStock/{eventoId}/{cantidad}")
    void crearReserva(@PathVariable("eventoId") Long eventoId, @PathVariable("cantidad") Integer cantidad);

    @PutMapping("/api/v0/Eventos/restaurarStock/{eventoId}/{cantidad}")
    void liberarReserva(@PathVariable("eventoId") Long eventoId, @PathVariable("cantidad") Integer cantidad);
}
