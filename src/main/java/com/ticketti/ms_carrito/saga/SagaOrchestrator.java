package com.ticketti.ms_carrito.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.dto.CheckoutDto;
import com.ticketti.ms_carrito.dto.ReservaRequestDto;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPedido;
import com.ticketti.ms_carrito.model.IdempotencyRecord;
import com.ticketti.ms_carrito.model.ItemPedido;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.model.Pago;
import com.ticketti.ms_carrito.model.Pedido;
import com.ticketti.ms_carrito.model.Reserva;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.IdempotencyRecordRepository;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;
import com.ticketti.ms_carrito.repository.PagoRepository;
import com.ticketti.ms_carrito.repository.PedidoRepository;
import com.ticketti.ms_carrito.repository.ReservaRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

	private static final int MAX_ENTRADAS = 4;
	private static final int MINUTOS_RESERVA = 5;

	private final CarritoRepository carritoRepository;
	private final PedidoRepository pedidoRepository;
	private final ReservaRepository reservaRepository;
	private final PagoRepository pagoRepository;
	private final IdempotencyRecordRepository idempotencyRepository;
	private final OutboxEventRepository outboxRepository;
	private final EventoClient eventoClient;

	@Transactional
	@CircuitBreaker(name = "checkoutSaga", fallbackMethod = "checkoutFallback")
	@TimeLimiter(name = "checkoutSaga")
	public CompletableFuture<Pedido> ejecutarCheckout(Long carritoId, Long usuarioId, CheckoutDto dto) {
		return CompletableFuture.completedFuture(ejecutarCheckoutInterno(carritoId, usuarioId, dto));
	}

	public CompletableFuture<Pedido> checkoutFallback(Long carritoId, Long usuarioId, CheckoutDto dto, Throwable ex) {
		log.error("[SAGA] CircuitBreaker abierto o falla de checkout para carrito {}: {}", carritoId, ex.getMessage());
		return CompletableFuture.failedFuture(CarritoException.pagoFallido("Servicio no disponible temporalmente. Intente más tarde."));
	}

	private Pedido ejecutarCheckoutInterno(Long carritoId, Long usuarioId, CheckoutDto dto) {
		log.info("[SAGA] Iniciando checkout para carrito: {}, usuario: {}", carritoId, usuarioId);

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

			procesarPago(pedido, dto);
			pedido.marcarPagado();
			pedido = pedidoRepository.save(pedido);

			guardarOutboxEvent(pedido, "pago.aprobado");
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

	private void validarIdempotencia(CheckoutDto dto) {
		if (dto.getIdempotencyKey() == null || dto.getIdempotencyKey().isBlank()) {
			throw CarritoException.idempotenciaInvalida();
		}

		idempotencyRepository.findByKey(dto.getIdempotencyKey()).ifPresent(record -> {
			if (record.isProcesado()) {
				throw CarritoException.idempotenciaInvalida();
			}
		});

		if (idempotencyRepository.existsByKey(dto.getIdempotencyKey())) {
			return;
		}

		IdempotencyRecord record = new IdempotencyRecord();
		record.setKey(dto.getIdempotencyKey());
		record.setRequestHash(dto.getRequestHash());
		record.setStatus(IdempotencyRecord.Status.PENDING);
		record.setCreatedAt(LocalDateTime.now());
		record.setExpiresAt(LocalDateTime.now().plusMinutes(30));
		idempotencyRepository.save(record);
	}

	private void marcarIdempotenciaCompletada(String idempotencyKey, Pedido pedido) {
		idempotencyRepository.findByKey(idempotencyKey).ifPresent(record -> {
			record.marcarCompletado("{\"pedidoId\": " + pedido.getId() + "}");
			idempotencyRepository.save(record);
		});
	}

	private void marcarIdempotenciaFallida(String idempotencyKey) {
		idempotencyRepository.findByKey(idempotencyKey).ifPresent(record -> {
			record.marcarFallido();
			idempotencyRepository.save(record);
		});
	}

	private void validarPropiedadCarrito(CarritoDeCompras carrito, Long usuarioId) {
		if (!carrito.getUsuarioId().equals(usuarioId)) {
			throw CarritoException.accesoNoAutorizado();
		}
	}

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

	private void reservarStock(Pedido pedido, CarritoDeCompras carrito) {
		pedido.getItems().forEach(item -> {
			try {
				ReservaRequestDto reservaRequest = new ReservaRequestDto();
				reservaRequest.setCantidadEntradas(item.getCantidad());
				reservaRequest.setIdUsuario(pedido.getUserId());

				String reservaId = eventoClient.crearReserva(item.getEventoId(), reservaRequest);
				Long idReserva = Long.parseLong(reservaId);
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
			} catch (Exception ex) {
				log.error("Error reservando stock para evento {}: {}", item.getEventoId(), ex.getMessage());
				throw CarritoException.stockNoDisponible(item.getEventoId());
			}
		});
	}

	private void procesarPago(Pedido pedido, CheckoutDto dto) {
		log.info("[SAGA] Procesando pago para pedido: {}", pedido.getId());

		Pago pago = new Pago();
		pago.setMontoTotal(pedido.getTotal());
		pago.setTokenPasarela(dto.getToken());
		pago.setEstadoPago(Pago.EstadoPago.PAGO_APROBADO);
		pago.setIdempotencyKey(pedido.getIdempotencyKey());
		pago.setReservaIdReserva(pedido.getReservaId());
		pago.setCarritoIdCarrito(pedido.getId());
		pago.setFechaPago(LocalDateTime.now());
		pagoRepository.save(pago);
	}

	private void guardarOutboxEvent(Pedido pedido, String routingKey) {
		OutboxEvent event = new OutboxEvent();
		event.setAggregateId(pedido.getId());
		event.setType("PAGO_CONFIRMADO");
		event.setPayload(String.format(
				"{\"pedidoId\": %d, \"userId\": %d, \"monto\": %s, \"estado\": \"%s\"}",
				pedido.getId(), pedido.getUserId(), pedido.getTotal(), pedido.getEstadoPago()));
		event.setRoutingKey(routingKey);
		event.setStatus(OutboxEvent.Status.PENDING);
		event.setCreatedAt(LocalDateTime.now());
		outboxRepository.save(event);
	}

	private void ejecutarCompensacion(Pedido pedido) {
		log.info("[SAGA] Ejecutando compensación para pedido: {}", pedido.getId());

		pedido.getItems().forEach(item -> {
			if (item.getReservaId() != null) {
				try {
					eventoClient.liberarReserva(item.getEventoId(), String.valueOf(item.getReservaId()));
					reservaRepository.findById(item.getReservaId()).ifPresent(reserva -> {
						reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CANCELADA);
						reservaRepository.save(reserva);
					});
				} catch (Exception ex) {
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

	@Transactional
	public void procesarDevolucion(Long pedidoId, BigDecimal montoDevolucion) {
		log.info("[SAGA] Procesando devolución para pedido: {}", pedidoId);
		throw CarritoException.devolucionNoPermitida("Flujo de devolución no implementado todavía");
	}
}