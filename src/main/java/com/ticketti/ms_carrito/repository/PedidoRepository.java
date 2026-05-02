package com.ticketti.ms_carrito.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ticketti.ms_carrito.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
