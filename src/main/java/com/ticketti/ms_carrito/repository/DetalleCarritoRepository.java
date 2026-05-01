package com.ticketti.ms_carrito.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.model.DetalleCarrito;

@Repository
public interface DetalleCarritoRepository extends JpaRepository<DetalleCarrito, Long> {
    List<DetalleCarrito> findByIdCarritoDeCompras(Long idCarrito);

    void deleteByIdCarritoDeCompras(Long idCarrito);
}