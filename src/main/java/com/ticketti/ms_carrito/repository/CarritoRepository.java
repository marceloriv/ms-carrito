package com.ticketti.ms_carrito.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.EstadoCarrito;

@Repository
public interface CarritoRepository extends JpaRepository<CarritoDeCompras, Long> {

    List<CarritoDeCompras> findByUsuarioId(Long usuarioId);

    List<CarritoDeCompras> findByRolUsuarioId(Long rolUsuarioId);

    List<CarritoDeCompras> findByUsuarioIdAndEstadoCarrito(Long usuarioId, EstadoCarrito estado);

    Optional<CarritoDeCompras> findByIdempotencyKey(String idempotencyKey);

    Optional<CarritoDeCompras> findByReservaId(Long reservaId);
}