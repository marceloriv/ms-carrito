package com.ticketti.ms_carrito.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.dto.CheckoutDto;
import com.ticketti.ms_carrito.dto.ReservaRequestDto;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.messaging.CompraConfirmadaEvent;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.model.EstadoPedido;
import com.ticketti.ms_carrito.model.ItemPedido;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.model.Pago;
import com.ticketti.ms_carrito.model.Pedido;
import com.ticketti.ms_carrito.model.Reserva;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;
import com.ticketti.ms_carrito.repository.PagoRepository;
import com.ticketti.ms_carrito.repository.PedidoRepository;
import com.ticketti.ms_carrito.repository.ReservaRepository;
import com.ticketti.ms_carrito.service.IdempotencyService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

	private static final int MINUTOS_RESERVA = 5;
	private static final int MAX_ENTRADAS = 4;

	private final CarritoRepository carritoRepository;
	private final PedidoRepository pedidoRepository;
	private final ReservaRepository reservaRepository;
	private final PagoRepository pagoRepository;
	private final IdempotencyService idempotencyService;
	private final OutboxEventRepository outboxRepository;
	private final EventoClient eventoClient;
	private final ObjectMapper objectMapper;

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	/**
	 * Ejecuta el checkout orquestado del carrito.
	 *
	 * @param carritoId identificador del carrito.
	 * @param usuarioId identificador del usuario.
	 * @param dto datos del checkout.
	 * @return futura con el pedido creado.
	 */
	@Transactional
	@CircuitBreaker(name = "checkoutSaga", fallbackMethod = "respaldoCheckout")
	@TimeLimiter(name = "checkoutSaga")
	public CompletableFuture<Pedido> ejecutarCheckout(Long carritoId, Long usuarioId, CheckoutDto dto) {
		return CompletableFuture.completedFuture(ejecutarCheckoutInterno(carritoId, usuarioId, dto));
	}

	/**
	 * Maneja el respaldo cuando el circuito de checkout falla.
	 *
	 * @param carritoId identificador del carrito.
	 * @param usuarioId identificador del usuario.
	 * @param dto datos del checkout.
	 * @param ex causa del fallo.
	 * @return futura fallida con el error de negocio.
	 */
	public CompletableFuture<Pedido> respaldoCheckout(Long carritoId, Long usuarioId, CheckoutDto dto, Throwable ex) {
		log.error("[SAGA] CircuitBreaker abierto o falla de checkout para carrito {}, usuario {}: {}",
				carritoId, usuarioId, ex.getMessage());
		if (dto != null) {
			log.debug("[SAGA] DTO de respaldo recibido con idempotencyKey {}", dto.getIdempotencyKey());
		}
		return CompletableFuture.failedFuture(CarritoException.pagoFallido("Servicio no disponible temporalmente. Intente más tarde."));
	}

	/**
	 * Ejecuta la lógica interna del checkout de forma secuencial.
	 *
	 * @param carritoId identificador del carrito.
	 * @param usuarioId identificador del usuario.
	 * @param dto datos del checkout.
	 * @return pedido resultante.
	 */
	private Pedido ejecutarCheckoutInterno(Long carritoId, Long usuarioId, CheckoutDto dto) {
		log.info("[SAGA] Iniciando checkout para carrito: {}, usuario: {}", carritoId, usuarioId);
		validarEntrada(dto);

		validarIdempotencia(dto);

		CarritoDeCompras carrito = carritoRepository.findById(carritoId)
				.orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

		validarPropiedadCarrito(carrito, usuarioId);
		validarReglasNegocio(carrito);

		Pedido pedido = crearPedidoDesdeCarrito(carrito, dto);
		pedido = pedidoRepository.save(pedido);

		try {
			reservarStock(pedido, carrito);
			pedido.reservar();
			pedido = pedidoRepository.save(pedido);

			Long pagoId = procesarPago(pedido, dto);
			pedido.marcarPagado();
			pedido = pedidoRepository.save(pedido);

			carrito.setEstadoCarrito(EstadoCarrito.PAGADO);
			carrito.setEstadoPago(EstadoPago.PAGADO);
			carritoRepository.save(carrito);

			guardarEventoOutbox(pedido, pagoId, "pago.aprobado");
			marcarIdempotenciaCompletada(dto.getIdempotencyKey(), pedido);

			log.info("[SAGA] Checkout completado exitosamente para pedido: {}", pedido.getId());
			return pedido;
		} catch (Exception ex) {
			log.error("[SAGA] Error en checkout, iniciando compensación: {}", ex.getMessage());
			ejecutarCompensacion(pedido);
			marcarIdempotenciaFallida(dto.getIdempotencyKey());
			throw ex instanceof CarritoException carritoException
					? carritoException
					: new CarritoException("Checkout falló: " + ex.getMessage());
		}
	}

	/**
	 * Registra la solicitud idempotente del checkout.
	 *
	 * @param dto datos del checkout.
	 */
	private void validarIdempotencia(CheckoutDto dto) {
		idempotencyService.registrarSolicitud(dto.getIdempotencyKey(), dto.getRequestHash());
	}

	/**
	 * Marca la solicitud idempotente como completada.
	 *
	 * @param idempotencyKey clave idempotente.
	 * @param pedido pedido resultante.
	 */
	private void marcarIdempotenciaCompletada(String idempotencyKey, Pedido pedido) {
		idempotencyService.marcarCompletado(idempotencyKey, "{\"pedidoId\": " + pedido.getId() + "}");
	}

	/**
	 * Marca la solicitud idempotente como fallida.
	 *
	 * @param idempotencyKey clave idempotente.
	 */
	private void marcarIdempotenciaFallida(String idempotencyKey) {
		idempotencyService.marcarFallido(idempotencyKey);
	}

	/**
	 * Verifica que el carrito pertenezca al usuario.
	 *
	 * @param carrito carrito a revisar.
	 * @param usuarioId identificador del usuario.
	 */
	private void validarPropiedadCarrito(CarritoDeCompras carrito, Long usuarioId) {
		if (!carrito.getUsuarioId().equals(usuarioId)) {
			throw CarritoException.accesoNoAutorizado();
		}
	}

	/**
	 * Valida los datos de entrada usando Bean Validation.
	 *
	 * @param dto objeto a validar.
	 */
	private void validarEntrada(Object dto) {
		Set<ConstraintViolation<Object>> violations = validator.validate(dto);
		if (!violations.isEmpty()) {
			throw new ConstraintViolationException(violations);
		}
	}

	/**
	 * Verifica las reglas de negocio básicas antes de continuar con el checkout.
	 *
	 * @param carrito carrito a validar.
	 */
	private void validarReglasNegocio(CarritoDeCompras carrito) {
		if (carrito.getTotalEntradas() == 0) {
			throw CarritoException.carritoVacio();
		}

		if (carrito.getTotalEntradas() > MAX_ENTRADAS) {
			throw CarritoException.limiteEntradasExcedido(carrito.getTotalEntradas(), MAX_ENTRADAS);
		}

		if (carrito.getEstadoCarrito() == EstadoCarrito.PAGADO) {
			throw CarritoException.carritoYaPagado();
		}
	}

	/**
	 * Construye el pedido a partir del estado actual del carrito.
	 *
	 * @param carrito carrito de origen.
	 * @param dto datos del checkout.
	 * @return pedido construido.
	 */
	private Pedido crearPedidoDesdeCarrito(CarritoDeCompras carrito, CheckoutDto dto) {
		Pedido pedido = new Pedido();
		pedido.setUserId(carrito.getUsuarioId());
		pedido.setIdempotencyKey(dto.getIdempotencyKey());
		pedido.setSubtotal(carrito.getSubtotal());
		pedido.setMontoDonacion(carrito.getMontoDonacion());
		pedido.setTotal(carrito.getTotal());
		pedido.setCausaSocialId(carrito.getCausaSocialId());
		pedido.setReservaId(carrito.getReservaId());
		pedido.setFechaExpiracionReserva(carrito.getFechaExpiracionReserva());

		carrito.getDetalles().forEach(detalle -> {
			ItemPedido item = new ItemPedido();
			item.setEventoId(detalle.getEventoId());
			item.setTipoEntrada(detalle.getTipoEntradaNombre());
			item.setCantidad(detalle.getCantidad());
			item.setPrecioUnitario(detalle.getPrecioUnitario());
			pedido.agregarItem(item);
		});

		return pedido;
	}

	/**
	 * Reserva stock para cada ítem del pedido.
	 *
	 * @param pedido pedido en proceso.
	 * @param carrito carrito de origen.
	 */
	private void reservarStock(Pedido pedido, CarritoDeCompras carrito) {
		pedido.getItems().forEach(item -> {
			try {
				eventoClient.crearReserva(item.getEventoId(), item.getCantidad());
				Long idReserva = 0L;
				item.setReservaId(idReserva);
				if (pedido.getReservaId() == null) {
					pedido.setReservaId(idReserva);
				}

				Reserva localReserva = new Reserva();
				localReserva.setIdReserva(idReserva);
				localReserva.setFechaReserva(LocalDateTime.now());
				localReserva.setUsuarioIdUsu(carrito.getUsuarioId());
				localReserva.setRolUsuarioIdUsuRol(carrito.getRolUsuarioId());
				localReserva.setCarritoDeComprasIdCarrito(carrito.getIdCarrito());
				localReserva.setEventoId(item.getEventoId());
				localReserva.setCantidadEntradas(item.getCantidad());
				localReserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CONFIRMADA);
				localReserva.setFechaExpiracion(LocalDateTime.now().plusMinutes(MINUTOS_RESERVA));
				reservaRepository.save(localReserva);
			} catch (RuntimeException ex) {
				log.error("Error reservando stock para evento {}: {}", item.getEventoId(), ex.getMessage());
				throw CarritoException.stockNoDisponible(item.getEventoId());
			}
		});
	}

	/**
	 * Registra el pago aprobado en la base de datos.
	 *
	 * @param pedido pedido asociado al pago.
	 * @param dto datos del checkout.
	 * @return id del pago registrado.
	 */
	private Long procesarPago(Pedido pedido, CheckoutDto dto) {
		log.info("[SAGA] Procesando pago para pedido: {}", pedido.getId());

		Pago pago = new Pago();
		pago.setMontoTotal(pedido.getTotal());
		pago.setTokenPasarela(dto.getToken());
		pago.setEstadoPago(EstadoPago.PAGADO);
		pago.setIdempotencyKey(pedido.getIdempotencyKey());
		pago.setReservaIdReserva(pedido.getReservaId());
		pago.setCarritoIdCarrito(pedido.getId());
		pago.setFechaPago(LocalDateTime.now());
		pago = pagoRepository.save(pago);
		return pago.getIdPago();
	}

	/**
	 * Guarda en outbox el evento que notifica el pago confirmado.
	 *
	 * @param pedido pedido procesado.
	 * @param pagoId id del pago registrado.
	 * @param routingKey clave de enrutamiento.
	 */
	private void guardarEventoOutbox(Pedido pedido, Long pagoId, String routingKey) {
		try {
			CompraConfirmadaEvent evt = new CompraConfirmadaEvent();
			evt.setIdCarrito(pedido.getId());
			evt.setPagoId(pagoId);
			evt.setUsuarioId(pedido.getUserId());
			evt.setCausaSocialId(pedido.getCausaSocialId());
			evt.setTotal(pedido.getTotal());
			evt.setMontoDonacion(pedido.getMontoDonacion());
			if (pedido.getItems() != null && !pedido.getItems().isEmpty()) {
				evt.setEventoId(pedido.getItems().get(0).getEventoId());
			}

			String payload = objectMapper.writeValueAsString(evt);

			OutboxEvent event = new OutboxEvent();
			event.setAggregateId(pedido.getId());
			event.setType("PAGO_CONFIRMADO");
			event.setPayload(payload);
			event.setRoutingKey(routingKey);
			event.setStatus(OutboxEvent.Status.PENDING);
			event.setCreatedAt(LocalDateTime.now());
			outboxRepository.save(event);
		} catch (JsonProcessingException e) {
			log.error("Error serializando evento para outbox: {}", e.getMessage());
			throw new CarritoException("Error procesando evento");
		}
	}

	/**
	 * Ejecuta la compensación de la saga si ocurre un fallo.
	 *
	 * @param pedido pedido a compensar.
	 */
	private void ejecutarCompensacion(Pedido pedido) {
		log.info("[SAGA] Ejecutando compensación para pedido: {}", pedido.getId());

		pedido.getItems().forEach(item -> {
			if (item.getReservaId() != null) {
				try {
					eventoClient.liberarReserva(item.getEventoId(), item.getCantidad());
					reservaRepository.findById(item.getReservaId()).ifPresent(reserva -> {
						reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CANCELADA);
						reservaRepository.save(reserva);
					});
				} catch (RuntimeException ex) {
					log.error("Error liberando reserva {}: {}", item.getReservaId(), ex.getMessage());
				}
			}
		});

		try {
			pedido.marcarPagoFallido();
			pedido.setEstadoPedido(EstadoPedido.CANCELADO);
		} catch (Exception ex) {
			log.warn("No se pudo marcar el pedido como fallido: {}", ex.getMessage());
		}

		OutboxEvent revertEvent = new OutboxEvent();
		revertEvent.setAggregateId(pedido.getId());
		revertEvent.setType("COMPRA_REVERTIDA");
		revertEvent.setPayload(String.format(
				"{\"pedidoId\": %d, \"razon\": \"compensacion_saga\"}",
				pedido.getId()));
		revertEvent.setRoutingKey("compra.revertida");
		revertEvent.setStatus(OutboxEvent.Status.PENDING);
		revertEvent.setCreatedAt(LocalDateTime.now());
		outboxRepository.save(revertEvent);
	}

	/**
	 * Mantiene pendiente el flujo de devolución de la saga.
	 *
	 * @param pedidoId identificador del pedido.
	 * @param montoDevolucion monto solicitado.
	 */
	@Transactional
	public void procesarDevolucion(Long pedidoId, BigDecimal montoDevolucion) {
		log.info("[SAGA] Procesando devolución para pedido: {}", pedidoId);
		throw CarritoException.devolucionNoPermitida("Flujo de devolución no implementado todavía");
	}
}
