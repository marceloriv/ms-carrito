package com.ticketti.ms_carrito.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Estados del carrito con métodos fábrica para validar transiciones. Usa un
 * patrón de estado para garantizar que sólo se permitan transiciones válidas.
 */
public enum EstadoCarrito {
    CREADO,
    RESERVADO,
    PAGADO,
    FALLIDO,
    CANCELADO,
    REEMBOLSADO;

    private static final Map<EstadoCarrito, Set<EstadoCarrito>> TRANSICIONES = Map.ofEntries(
            entry(CREADO, EnumSet.of(RESERVADO, CANCELADO)),
            entry(RESERVADO, EnumSet.of(PAGADO, FALLIDO, CANCELADO)),
            entry(PAGADO, EnumSet.of(REEMBOLSADO)),
            entry(FALLIDO, EnumSet.of(CREADO)),
            entry(CANCELADO, EnumSet.noneOf(EstadoCarrito.class)),
            entry(REEMBOLSADO, EnumSet.noneOf(EstadoCarrito.class))
    );

    /**
     * Factory method: valida y retorna el nuevo estado si la transición es
     * válida.
     *
     * @param estadoActual Estado actual del carrito
     * @param nuevoEstado Estado deseado
     * @return EstadoCarrito validado
     * @throws IllegalStateException si la transición no es permitida
     */
    public static EstadoCarrito transition(EstadoCarrito estadoActual, EstadoCarrito nuevoEstado) {
        if (!estadoActual.puedeTransicionarA(nuevoEstado)) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s", estadoActual, nuevoEstado));
        }
        return nuevoEstado;
    }

    /**
     * Factory method con validación de negocio para pagos. Solo permite PAGADO
     * si hay items en el carrito.
     */
    public static EstadoCarrito pagar(EstadoCarrito estadoActual, int totalItems) {
        if (totalItems == 0) {
            throw new IllegalStateException("No se puede pagar un carrito vacío");
        }
        return transition(estadoActual, PAGADO);
    }

    /**
     * Factory method para cancelar con validación.
     */
    public static EstadoCarrito cancelar(EstadoCarrito estadoActual) {
        if (estadoActual == PAGADO || estadoActual == REEMBOLSADO) {
            throw new IllegalStateException("No se puede cancelar un carrito ya pagado o reembolsado");
        }
        return transition(estadoActual, CANCELADO);
    }

    public static EstadoCarrito reembolsar(EstadoCarrito estadoActual) {
        return transition(estadoActual, REEMBOLSADO);
    }

    public boolean puedeTransicionarA(EstadoCarrito nuevoEstado) {
        return TRANSICIONES.get(this).contains(nuevoEstado);
    }
}
