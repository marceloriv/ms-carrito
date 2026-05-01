package com.ticketti.ms_carrito.model;

import com.ticketti.ms_carrito.exception.CarritoException;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * EstadoPedido - Estados del pedido según ERS RF-3. CREADO → RESERVADO →
 * [CANCELADO | REEMBOLSADO]
 */
@Getter
public enum EstadoPedido {
    CREADO("Creado"),
    RESERVADO("Reservado"),
    CANCELADO("Cancelado"),
    REEMBOLSADO("Reembolsado");

    private final String descripcion;
    private Set<EstadoPedido> transicionesPermitidas;

    static {
        CREADO.transicionesPermitidas = EnumSet.of(RESERVADO, CANCELADO);
        RESERVADO.transicionesPermitidas = EnumSet.of(CANCELADO, REEMBOLSADO);
        CANCELADO.transicionesPermitidas = EnumSet.noneOf(EstadoPedido.class);
        REEMBOLSADO.transicionesPermitidas = EnumSet.noneOf(EstadoPedido.class);
    }

    EstadoPedido(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * Valida transición de estado según máquina de estados.
     *
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
            "CREADO", CREADO,
            "RESERVADO", RESERVADO,
            "CANCELADO", CANCELADO,
            "REEMBOLSADO", REEMBOLSADO
    );

    /**
     * Factory method: Crea estado desde string
     *
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
