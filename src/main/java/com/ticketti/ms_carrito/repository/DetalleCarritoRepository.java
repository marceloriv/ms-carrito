package com.ticketti.ms_carrito.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.dto.EstadisticaEventoDto;
import com.ticketti.ms_carrito.model.DetalleCarrito;

@Repository
public interface DetalleCarritoRepository extends JpaRepository<DetalleCarrito, Long> {
    List<DetalleCarrito> findByIdCarritoDeCompras(Long idCarrito);

    void deleteByIdCarritoDeCompras(Long idCarrito);

    @Query("SELECT new com.ticketti.ms_carrito.dto.EstadisticaEventoDto(" +
           "d.eventoId, " +
           "COALESCE(SUM(CASE WHEN c.estadoCarrito = com.ticketti.ms_carrito.model.EstadoCarrito.PAGADO THEN d.cantidad ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN c.estadoCarrito = com.ticketti.ms_carrito.model.EstadoCarrito.PAGADO THEN d.cantidad * d.precioUnitario ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN c.estadoCarrito = com.ticketti.ms_carrito.model.EstadoCarrito.REEMBOLSADO THEN d.cantidad ELSE 0 END), 0)) " +
           "FROM DetalleCarrito d JOIN d.carrito c " +
           "WHERE c.estadoCarrito IN (com.ticketti.ms_carrito.model.EstadoCarrito.PAGADO, com.ticketti.ms_carrito.model.EstadoCarrito.REEMBOLSADO) " +
           "AND d.eventoId IN :eventoIds " +
           "GROUP BY d.eventoId")
    List<EstadisticaEventoDto> estadisticasPorEventos(@Param("eventoIds") List<Long> eventoIds);
}