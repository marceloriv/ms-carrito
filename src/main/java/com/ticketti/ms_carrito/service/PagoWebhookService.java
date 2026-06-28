package com.ticketti.ms_carrito.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.dto.WebhookPagoDto;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.messaging.CompraConfirmadaEvent;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.model.Pago;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;
import com.ticketti.ms_carrito.repository.PagoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoWebhookService {

    private final CarritoRepository carritoRepository;
    private final PagoRepository pagoRepository;
    private final OutboxEventRepository outboxRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final CarritoService carritoService;

    /**
     * Procesa un pago aprobado y deja persistidos el carrito, el pago y el outbox.
     * También crea un nuevo carrito vacío para el usuario para futuras compras.
     *
     * @param carrito carrito que será marcado como pagado.
     * @param dto datos del webhook recibido.
     * @return carrito actualizado.
     */
    @Transactional
    public CarritoDeCompras procesarPagoAprobado(CarritoDeCompras carrito, WebhookPagoDto dto) {
        carrito.setEstadoCarrito(EstadoCarrito.PAGADO);
        carrito.setEstadoPago(EstadoPago.PAGADO);
        carrito.setFechaUltActualizacion(LocalDateTime.now());
        carrito = carritoRepository.save(carrito);

        Pago pago = new Pago();
        pago.setMontoTotal(carrito.getTotal());
        pago.setCarritoIdCarrito(carrito.getIdCarrito());
        pago.setReservaIdReserva(carrito.getReservaId());
        pago.setIdempotencyKey(carrito.getIdempotencyKey());
        pago.setTokenPasarela(dto.getToken());
        pago.setEstadoPago(EstadoPago.PAGADO);
        pago = pagoRepository.save(pago);
        carrito.setIdPago(pago.getIdPago());

        guardarEventoOutbox(carrito, "pago.aprobado");

        if (carrito.getIdempotencyKey() != null) {
            idempotencyService.marcarCompletado(carrito.getIdempotencyKey(),
                    "{\"carritoId\": " + carrito.getIdCarrito() + ", \"estado\": \"PAGADO\"}");
        }

        // Crear un nuevo carrito vacío para el usuario para futuras compras
        try {
            String rolUsuario = "CLIENTE"; // El rol del usuario no está en el carrito, se usa valor por defecto
            CarritoDeCompras nuevoCarrito = carritoService.crearCarrito(carrito.getUsuarioId(), rolUsuario);
            log.info("Nuevo carrito vacío creado con ID {} para usuario {} después de pago exitoso",
                    nuevoCarrito.getIdCarrito(), carrito.getUsuarioId());
        } catch (Exception e) {
            log.error("Error creando nuevo carrito para usuario {} después de pago: {}",
                    carrito.getUsuarioId(), e.getMessage());
            // No lanzamos excepción para no interrumpir el flujo de pago
        }

        return carrito;
    }

    /**
     * Procesa un pago rechazado y actualiza el estado del carrito.
     *
     * @param carrito carrito afectado por el rechazo.
     * @return carrito actualizado.
     */
    @Transactional
    public CarritoDeCompras procesarPagoRechazado(CarritoDeCompras carrito) {
        carrito.setEstadoCarrito(EstadoCarrito.FALLIDO);
        carrito.setEstadoPago(EstadoPago.FALLIDO);
        carrito.setFechaUltActualizacion(LocalDateTime.now());
        carrito = carritoRepository.save(carrito);

        if (carrito.getIdempotencyKey() != null) {
            idempotencyService.marcarFallido(carrito.getIdempotencyKey());
        }

        return carrito;
    }

    /**
     * Guarda un evento de outbox con el estado actual del carrito.
     *
     * @param carrito carrito serializado en el payload.
     * @param tipo tipo lógico del evento.
     */
    private void guardarEventoOutbox(CarritoDeCompras carrito, String tipo) {
        try {
            CompraConfirmadaEvent evt = new CompraConfirmadaEvent();
            evt.setIdCarrito(carrito.getIdCarrito());
            evt.setPagoId(carrito.getIdPago());
            evt.setUsuarioId(carrito.getUsuarioId());
            evt.setCausaSocialId(carrito.getCausaSocialId());
            evt.setTotal(carrito.getTotal());
            evt.setMontoDonacion(carrito.getMontoDonacion());
            if (carrito.getDetalles() != null && !carrito.getDetalles().isEmpty()) {
                evt.setEventoId(carrito.getDetalles().get(0).getEventoId().longValue());
            }

            String payload = objectMapper.writeValueAsString(evt);

            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(carrito.getIdCarrito());
            event.setType(tipo);
            event.setPayload(payload);
            event.setStatus(OutboxEvent.Status.PENDING);
            event.setRoutingKey(tipo);
            event.setCreatedAt(LocalDateTime.now());

            outboxRepository.save(event);
            log.info("Outbox event guardado: {} para carrito {}", tipo, carrito.getIdCarrito());
        } catch (JsonProcessingException e) {
            log.error("Error serializando evento para outbox: {}", e.getMessage());
            throw new CarritoException("Error procesando evento");
        }
    }
}
