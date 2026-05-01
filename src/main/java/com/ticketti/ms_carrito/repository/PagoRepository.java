package com.ticketti.ms_carrito.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.model.Pago;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByCarritoIdCarrito(Long carritoId);

    Optional<Pago> findByIdempotencyKey(String idempotencyKey);

    Optional<Pago> findByReservaIdReserva(Long reservaId);
}