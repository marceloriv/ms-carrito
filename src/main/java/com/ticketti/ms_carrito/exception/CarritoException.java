package com.ticketti.ms_carrito.exception;

/**
 * Excepción unificada para el dominio del carrito de compras.
 * Centraliza todos los errores de negocio relacionados con:
 * - Stock y reservas
 * - Límites de entradas
 * - Pagos y devoluciones
 * - Idempotencia
 */
public class CarritoException extends RuntimeException {

    public enum CodigoError {
        DESCONOCIDO,
        STOCK_NO_DISPONIBLE,
        LIMITE_ENTRADAS_EXCEDIDO,
        CARRO_NO_ENCONTRADO,
        DETALLE_NO_ENCONTRADO,
        RESERVA_EXPIRADA,
        RESERVA_YA_RENOVADA,
        IDEMPOTENCIA_INVALIDA,
        PAGO_NO_APROBADO,
        DEVOLUCION_NO_PERMITIDA,
        CARRITO_VACIO,
        CARRITO_YA_PAGADO,
        CARRITO_CANCELADO,
        ACCESO_NO_AUTORIZADO,
        WEBHOOK_INVALIDO,
        TRANSICION_INVALIDA,
        PAGO_FALLIDO,
        HMAC_INVALIDO,
        TIMESTAMP_INVALIDO,
        NONCE_REPETIDO
    }

    private final CodigoError codigo;

    public CarritoException(String message) {
        this(CodigoError.DESCONOCIDO, message);
    }

    public CarritoException(CodigoError codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public CodigoError getCodigo() {
        return codigo;
    }

    public static CarritoException stockNoDisponible(Long eventoId) {
        return new CarritoException(CodigoError.STOCK_NO_DISPONIBLE,
                "No hay stock disponible para el evento: " + eventoId);
    }

    public static CarritoException limiteEntradasExcedido(int actual, int maximo) {
        return new CarritoException(CodigoError.LIMITE_ENTRADAS_EXCEDIDO,
            String.format("Máximo %d entradas por compra. Actualmente tiene: %d", maximo, actual));
    }

    public static CarritoException carritoNoEncontrado(Long carritoId) {
        return new CarritoException(CodigoError.CARRO_NO_ENCONTRADO, "Carrito no encontrado: " + carritoId);
    }

    public static CarritoException detalleNoEncontrado(Long detalleId) {
        return new CarritoException(CodigoError.DETALLE_NO_ENCONTRADO, "Detalle no encontrado: " + detalleId);
    }

    public static CarritoException reservaExpirada() {
        return new CarritoException(CodigoError.RESERVA_EXPIRADA, "La reserva ha expirado o no puede renovarse");
    }

    public static CarritoException reservaYaRenovada() {
        return new CarritoException(CodigoError.RESERVA_YA_RENOVADA, "La reserva ya fue renovada anteriormente");
    }

    public static CarritoException idempotenciaInvalida() {
        return new CarritoException(CodigoError.IDEMPOTENCIA_INVALIDA, "Idempotency key ya existe o es inválida");
    }

    public static CarritoException pagoNoAprobado() {
        return new CarritoException(CodigoError.PAGO_NO_APROBADO, "El pago no fue aprobado");
    }

    public static CarritoException devolucionNoPermitida(String razon) {
        return new CarritoException(CodigoError.DEVOLUCION_NO_PERMITIDA, "Devolución no permitida: " + razon);
    }

    public static CarritoException carritoVacio() {
        return new CarritoException(CodigoError.CARRITO_VACIO, "El carrito está vacío");
    }

    public static CarritoException carritoYaPagado() {
        return new CarritoException(CodigoError.CARRITO_YA_PAGADO, "No se pueden modificar carritos ya pagados");
    }

    public static CarritoException carritoCancelado() {
        return new CarritoException(CodigoError.CARRITO_CANCELADO, "No se pueden modificar carritos cancelados");
    }

    public static CarritoException accesoNoAutorizado() {
        return new CarritoException(CodigoError.ACCESO_NO_AUTORIZADO, "El carrito no pertenece al usuario");
    }

    public static CarritoException webhookInvalido(String razon) {
        return new CarritoException(CodigoError.WEBHOOK_INVALIDO, "Webhook inválido: " + razon);
    }

    public static CarritoException transicionEstadoInvalida(String estadoActual, String estadoNuevo) {
        return new CarritoException(CodigoError.TRANSICION_INVALIDA,
            String.format("Transición de estado inválida: %s → %s", estadoActual, estadoNuevo));
    }

    public static CarritoException pagoFallido(String razon) {
        return new CarritoException(CodigoError.PAGO_FALLIDO, "El pago falló: " + razon);
    }

    public static CarritoException hmacInvalido() {
        return new CarritoException(CodigoError.HMAC_INVALIDO, "Firma HMAC inválida o ausente");
    }

    public static CarritoException timestampInvalido() {
        return new CarritoException(CodigoError.TIMESTAMP_INVALIDO, "Timestamp fuera del rango permitido (±5 minutos)");
    }

    public static CarritoException nonceRepetido() {
        return new CarritoException(CodigoError.NONCE_REPETIDO, "Nonce ya fue utilizado anteriormente");
    }
}
