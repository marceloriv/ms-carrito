package com.ticketti.ms_carrito.model;

import com.ticketti.ms_carrito.exception.CarritoException;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * EstadoPago - Máquina de estados para pagos. Transiciones principales:
 * PENDIENTE → [PAGADO | FALLIDO] PAGADO → REEMBOLSADO
 */
@Getter
public enum EstadoPago {
    PENDIENTE("Pendiente"),
    PAGADO("Pagado"),
    FALLIDO("Fallido"),
    REEMBOLSADO("Reembolsado");

    private final String descripcion;
    private Set<EstadoPago> transicionesPermitidas;

    static {
        PENDIENTE.transicionesPermitidas = EnumSet.of(PAGADO, FALLIDO);
        PAGADO.transicionesPermitidas = EnumSet.of(REEMBOLSADO);
        FALLIDO.transicionesPermitidas = EnumSet.of(PENDIENTE);
        REEMBOLSADO.transicionesPermitidas = EnumSet.noneOf(EstadoPago.class);
    }

    EstadoPago(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * Valida transición de estado según máquina de estados.
     *
     * @throws CarritoException si la transición no es válida
     */
    public void validarTransicion(EstadoPago nuevoEstado) {
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

    private static final Map<String, EstadoPago> LOOKUP = Map.of(
            "PENDIENTE", PENDIENTE,
            "PAGADO", PAGADO,
            "FALLIDO", FALLIDO,
            "REEMBOLSADO", REEMBOLSADO
    );

    /**
     * Factory method: Crea estado desde string
     *
     * @throws IllegalArgumentException si el estado no existe
     */
    public static EstadoPago fromString(String estado) {
        EstadoPago result = LOOKUP.get(estado.toUpperCase());
        if (result == null) {
            throw new IllegalArgumentException("Estado no válido: " + estado);
        }
        return result;
    }
}
