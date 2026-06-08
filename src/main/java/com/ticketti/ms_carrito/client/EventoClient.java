package com.ticketti.ms_carrito.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ticketti.ms_carrito.client.dto.EventoInfoDto;
import com.ticketti.ms_carrito.dto.ReservaRequestDto;

@FeignClient(name = "ms-eventos", url = "${ms-eventos.url:http://localhost:8081}")
public interface EventoClient {

    @GetMapping("/api/v0/Eventos/buscarEvento/{eventoId}")
    EventoInfoDto buscarEvento(@PathVariable Integer eventoId);

    @PostMapping("/api/v1/eventos/{eventoId}/reservas")
    String crearReserva(@PathVariable Long eventoId, @RequestBody ReservaRequestDto request);

    @PostMapping("/api/v1/eventos/{eventoId}/reservas/{reservaId}/liberar")
    void liberarReserva(@PathVariable Long eventoId, @PathVariable String reservaId);
}
