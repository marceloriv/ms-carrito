package com.ticketti.ms_carrito.model;

import com.ticketti.ms_carrito.exception.CarritoException;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * EstadoPedido - Estados del pedido según ERS RF-3.
 * CREATED → RESERVED → [CANCELLED | REFUNDED]
 */
@Getter
public enum EstadoPedido {
    CREATED("Creado"),
    RESERVED("Reservado"),
    CANCELLED("Cancelado"),
    REFUNDED("Reembolsado");

    private final String descripcion;
    private Set<EstadoPedido> transicionesPermitidas;

    static {
        CREATED.transicionesPermitidas = EnumSet.of(RESERVED, CANCELLED);
        RESERVED.transicionesPermitidas = EnumSet.of(CANCELLED, REFUNDED);
        CANCELLED.transicionesPermitidas = EnumSet.noneOf(EstadoPedido.class);
        REFUNDED.transicionesPermitidas = EnumSet.noneOf(EstadoPedido.class);
    }

    EstadoPedido(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * Valida transición de estado según máquina de estados.
     * @throws CarritoException si la transición no es válida
     */
    public void validarTransicion(EstadoPedido nuevoEstado) {
        if (!transicionesPermitidas.contains(nuevoEstado)) {
            throw CarritoException.transicionEstadoInvalida(this.name(), nuevoEstado.name());
        }
    }

    /**
     * Verifica si el estado es final (no admite más transiciones)
     */
    public boolean isEstadoFinal() {
        return transicionesPermitidas.isEmpty();
    }

    private static final Map<String, EstadoPedido> LOOKUP = Map.of(
            "CREATED", CREATED,
            "RESERVED", RESERVED,
            "CANCELLED", CANCELLED,
            "REFUNDED", REFUNDED
    );

    /**
     * Factory method: Crea estado desde string
     * @throws IllegalArgumentException si el estado no existe
     */
    public static EstadoPedido fromString(String estado) {
        EstadoPedido result = LOOKUP.get(estado.toUpperCase());
        if (result == null) {
            throw new IllegalArgumentException("Estado no válido: " + estado);
        }
        return result;
    }
}
