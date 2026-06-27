package com.ticketti.ms_carrito.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.model.Reserva;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.ReservaRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LimpiezaReservasService {

    private static final List<Reserva.EstadoReserva> ESTADOS_ACTIVOS = List.of(
            Reserva.EstadoReserva.RESERVA_INICIADA,
            Reserva.EstadoReserva.RESERVA_CONFIRMADA,
            Reserva.EstadoReserva.RESERVA_PENDIENTE
    );

    private final ReservaRepository reservaRepository;
    private final CarritoRepository carritoRepository;
    private final EventoClient eventoClient;

    public LimpiezaReservasService(ReservaRepository reservaRepository,
                                    CarritoRepository carritoRepository,
                                    EventoClient eventoClient) {
        this.reservaRepository = reservaRepository;
        this.carritoRepository = carritoRepository;
        this.eventoClient = eventoClient;
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void limpiarReservasExpiradas() {
        List<Reserva> expiradas = reservaRepository
                .findByEstadoReservaInAndFechaExpiracionBefore(ESTADOS_ACTIVOS, LocalDateTime.now());

        if (expiradas.isEmpty()) {
            return;
        }

        log.info("Limpiando {} reservas expiradas", expiradas.size());

        for (Reserva reserva : expiradas) {
            try {
                eventoClient.liberarReserva(reserva.getEventoId(), reserva.getCantidadEntradas());

                reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CANCELADA);
                reservaRepository.save(reserva);

                carritoRepository.findByReservaId(reserva.getIdReserva()).ifPresent(carrito -> {
                    carrito.setEstadoCarrito(EstadoCarrito.CANCELADO);
                    carrito.setEstadoPago(EstadoPago.FALLIDO);
                    carritoRepository.save(carrito);
                });

                log.info("Reserva {} liberada y cancelada por expiracion", reserva.getIdReserva());
            } catch (Exception e) {
                log.error("Error al limpiar reserva {}: {}", reserva.getIdReserva(), e.getMessage());
            }
        }
    }
}
