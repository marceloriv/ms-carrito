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

    public CarritoException(String message) {
        super(message);
    }

    public static CarritoException stockNoDisponible(Long eventoId) {
        return new CarritoException("No hay stock disponible para el evento: " + eventoId);
    }

    public static CarritoException limiteEntradasExcedido(int actual, int maximo) {
        return new CarritoException(
            String.format("Máximo %d entradas por compra. Actualmente tiene: %d", maximo, actual));
    }

    public static CarritoException carritoNoEncontrado(Long carritoId) {
        return new CarritoException("Carrito no encontrado: " + carritoId);
    }

    public static CarritoException reservaExpirada() {
        return new CarritoException("La reserva ha expirado o no puede renovarse");
    }

    public static CarritoException reservaYaRenovada() {
        return new CarritoException("La reserva ya fue renovada anteriormente");
    }

    public static CarritoException idempotenciaInvalida() {
        return new CarritoException("Idempotency key ya existe o es inválida");
    }

    public static CarritoException pagoNoAprobado() {
        return new CarritoException("El pago no fue aprobado");
    }

    public static CarritoException devolucionNoPermitida(String razon) {
        return new CarritoException("Devolución no permitida: " + razon);
    }

    public static CarritoException carritoVacio() {
        return new CarritoException("El carrito está vacío");
    }

    public static CarritoException carritoYaPagado() {
        return new CarritoException("No se pueden modificar carritos ya pagados");
    }

    public static CarritoException carritoCancelado() {
        return new CarritoException("No se pueden modificar carritos cancelados");
    }

    public static CarritoException accesoNoAutorizado() {
        return new CarritoException("El carrito no pertenece al usuario");
    }

    public static CarritoException webhookInvalido(String razon) {
        return new CarritoException("Webhook inválido: " + razon);
    }

    public static CarritoException transicionEstadoInvalida(String estadoActual, String estadoNuevo) {
        return new CarritoException(
            String.format("Transición de estado inválida: %s → %s", estadoActual, estadoNuevo));
    }

    public static CarritoException pagoFallido(String razon) {
        return new CarritoException("El pago falló: " + razon);
    }

    public static CarritoException hmacInvalido() {
        return new CarritoException("Firma HMAC inválida o ausente");
    }

    public static CarritoException timestampInvalido() {
        return new CarritoException("Timestamp fuera del rango permitido (±5 minutos)");
    }

    public static CarritoException nonceRepetido() {
        return new CarritoException("Nonce ya fue utilizado anteriormente");
    }
}
