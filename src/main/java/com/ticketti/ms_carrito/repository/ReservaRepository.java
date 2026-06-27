package com.ticketti.ms_carrito.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.model.Reserva;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByCarritoDeComprasIdCarrito(Long carritoId);

    List<Reserva> findByUsuarioIdUsu(Long usuarioId);

    Optional<Reserva> findByEventoIdAndUsuarioIdUsu(Long eventoId, Long usuarioId);

    List<Reserva> findByEstadoReservaInAndFechaExpiracionBefore(List<Reserva.EstadoReserva> estados, LocalDateTime fecha);
}