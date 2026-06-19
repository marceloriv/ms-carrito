package com.ticketti.ms_carrito.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.client.CausaSocialClient;
import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.client.UsuarioClient;
import com.ticketti.ms_carrito.client.dto.CausaSocialInfoDto;
import com.ticketti.ms_carrito.client.dto.EventoInfoDto;
import com.ticketti.ms_carrito.client.dto.UsuarioInfoDto;
import com.ticketti.ms_carrito.dto.AgregarEntradaDto;
import com.ticketti.ms_carrito.dto.CheckoutDto;
import com.ticketti.ms_carrito.dto.DevolucionRequestDto;
import com.ticketti.ms_carrito.dto.DevolucionResponseDto;
import com.ticketti.ms_carrito.dto.ReservaRequestDto;
import com.ticketti.ms_carrito.dto.ResumenCarritoDto;
import com.ticketti.ms_carrito.dto.WebhookPagoDto;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.messaging.CompraConfirmadaEvent;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.DetalleCarrito;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.model.Pago;
import com.ticketti.ms_carrito.model.Reserva;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.DetalleCarritoRepository;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;
import com.ticketti.ms_carrito.repository.PagoRepository;
import com.ticketti.ms_carrito.repository.ReservaRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CarritoService {

    private static final int MINUTOS_RESERVA = 5;
    private static final int MINUTOS_RENOVACION = 2;
    private static final BigDecimal PORCENTAJE_REEMBOLSO = new BigDecimal("0.85");
    private static final int MAX_ENTRADAS = 4;
    private static final int WEBHOOK_TOLERANCE_MINUTES = 5;

    private final CarritoRepository carritoRepository;
    private final DetalleCarritoRepository detalleRepository;
    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final OutboxEventRepository outboxRepository;
    private final EventoClient eventoClient;
    private final IdempotencyService idempotencyService;
    private final PagoWebhookService pagoWebhookService;
    private final UsuarioClient usuarioClient;
    private final CausaSocialClient causaSocialClient;
    private final ObjectMapper objectMapper;

    public CarritoService(CarritoRepository carritoRepository,
                         DetalleCarritoRepository detalleRepository,
                         ReservaRepository reservaRepository,
                         PagoRepository pagoRepository,
                         OutboxEventRepository outboxRepository,
                         EventoClient eventoClient,
                         IdempotencyService idempotencyService,
                         @Lazy PagoWebhookService pagoWebhookService,
                         UsuarioClient usuarioClient,
                         CausaSocialClient causaSocialClient,
                         ObjectMapper objectMapper) {
        this.carritoRepository = carritoRepository;
        this.detalleRepository = detalleRepository;
        this.reservaRepository = reservaRepository;
        this.pagoRepository = pagoRepository;
        this.outboxRepository = outboxRepository;
        this.eventoClient = eventoClient;
        this.idempotencyService = idempotencyService;
        this.pagoWebhookService = pagoWebhookService;
        this.usuarioClient = usuarioClient;
        this.causaSocialClient = causaSocialClient;
        this.objectMapper = objectMapper;
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * Crea un carrito vacío para el usuario indicado.
     */
    @Transactional
    public CarritoDeCompras crearCarrito(Long usuarioId, String rolUsuario) {
        log.info("Creando nuevo carrito para usuario: {}, rol: {}", usuarioId, rolUsuario);

        CarritoDeCompras carrito = new CarritoDeCompras();
        carrito.setUsuarioId(usuarioId);
        carrito.setRolUsuarioId(0L); // valor placeholder — el rol real es el nombre del JWT
        carrito.setEstadoCarrito(EstadoCarrito.CREADO);
        carrito.setEstadoPago(EstadoPago.PENDIENTE);
        carrito.setSubtotal(BigDecimal.ZERO);
        carrito.setMontoDonacion(BigDecimal.ZERO);
        carrito.setTotal(BigDecimal.ZERO);
        carrito.setRenovacionUsada(false);
        carrito.setFechaCreacion(LocalDateTime.now());
        carrito.setFechaUltActualizacion(LocalDateTime.now());
        carrito.setDetalles(new ArrayList<>());

        return carritoRepository.save(carrito);
    }

    /**
     * Agrega o actualiza una entrada dentro del carrito.
     */
    @Transactional
    public CarritoDeCompras agregarEntrada(Long carritoId, Long usuarioId, AgregarEntradaDto dto) {

        validarEntrada(dto);
        log.info("Agregando entrada al carrito {}: {} x {}", carritoId, dto.getTipoEntrada(), dto.getCantidad());

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        if (carrito.getEstadoCarrito() == EstadoCarrito.PAGADO) {
            throw CarritoException.carritoYaPagado();
        }

        if (carrito.getEstadoCarrito() == EstadoCarrito.CANCELADO) {
            throw CarritoException.carritoCancelado();
        }

        int totalEntradasExistentes = carrito.getTotalEntradas();
        if (totalEntradasExistentes + dto.getCantidad() > MAX_ENTRADAS) {
            throw CarritoException.limiteEntradasExcedido(totalEntradasExistentes, MAX_ENTRADAS);
        }

        Optional<DetalleCarrito> detalleExistente = carrito.getDetalles().stream()
                .filter(d -> d.getEventoId().equals(dto.getEventoId()))
                .findFirst();

        if (detalleExistente.isPresent()) {
            DetalleCarrito detalle = detalleExistente.get();
            if (totalEntradasExistentes - detalle.getCantidad() + dto.getCantidad() > MAX_ENTRADAS) {
                throw CarritoException.limiteEntradasExcedido(MAX_ENTRADAS, MAX_ENTRADAS);
            }
            detalle.setCantidad(dto.getCantidad());
            detalle.setPrecioUnitario(dto.getPrecioUnitario());
        } else {
            DetalleCarrito detalle = new DetalleCarrito();
            detalle.setIdCarritoDeCompras(carritoId);
            detalle.setEventoId(dto.getEventoId());
            detalle.setTipoEntradaNombre(dto.getTipoEntrada());
            detalle.setCantidad(dto.getCantidad());
            detalle.setPrecioUnitario(dto.getPrecioUnitario());
            detalle.setCarrito(carrito);
            carrito.getDetalles().add(detalle);
        }

        carrito.recalcularTotales();
        carrito.setFechaUltActualizacion(LocalDateTime.now());

        return carritoRepository.save(carrito);
    }

    /**
     * Elimina una entrada específica del carrito.
     */
    @Transactional
    public CarritoDeCompras eliminarEntrada(Long carritoId, Long detalleId, Long usuarioId) {
        log.info("Eliminando detalle {} del carrito {}", detalleId, carritoId);

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        DetalleCarrito detalle = detalleRepository.findById(detalleId)
            .orElseThrow(() -> CarritoException.detalleNoEncontrado(detalleId));

        if (!detalle.getIdCarritoDeCompras().equals(carritoId)) {
            throw new CarritoException("El detalle no pertenece al carrito");
        }

        carrito.getDetalles().remove(detalle);
        detalleRepository.delete(detalle);

        carrito.recalcularTotales();
        carrito.setFechaUltActualizacion(LocalDateTime.now());

        return carritoRepository.save(carrito);
    }

    /**
     * Vacía el carrito y libera la reserva asociada si existe.
     */
    @Transactional
    public CarritoDeCompras vaciarCarrito(Long carritoId, Long usuarioId) {
        log.info("Vaciando carrito {}", carritoId);

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        if (carrito.getEstadoCarrito() == EstadoCarrito.RESERVADO && carrito.getReservaId() != null) {
            Reserva reserva = reservaRepository.findById(carrito.getReservaId()).orElse(null);
            if (reserva != null) {
                try {
                    eventoClient.liberarReserva(reserva.getEventoId(), reserva.getCantidadEntradas());
                    reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CANCELADA);
                    reservaRepository.save(reserva);
                } catch (Exception e) {
                    log.warn("No se pudo liberar reserva: {}", e.getMessage());
                }
            }
        }

        detalleRepository.deleteByIdCarritoDeCompras(carritoId);
        carrito.getDetalles().clear();
        carrito.recalcularTotales();
        carrito.setEstadoCarrito(EstadoCarrito.CANCELADO);
        carrito.setEstadoPago(EstadoPago.FALLIDO);
        carrito.setFechaUltActualizacion(LocalDateTime.now());

        return carritoRepository.save(carrito);
    }

    /**
     * Inicia el checkout usando la clave idempotente enviada por el cliente.
     */
    @Transactional
    public CarritoDeCompras iniciarCheckout(Long carritoId, Long usuarioId, CheckoutDto dto) {

        validarEntrada(dto);
        log.info("Iniciando checkout del carrito {} con causa social {}", carritoId, dto.getCausaSocialId());

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        if (carrito.getDetalles().isEmpty()) {
            throw CarritoException.carritoVacio();
        }

        if (carrito.getEstadoCarrito() != EstadoCarrito.CREADO &&
            carrito.getEstadoCarrito() != EstadoCarrito.RESERVADO) {
            throw new CarritoException("El carrito no está en estado válido para checkout");
        }

        int totalEntradas = carrito.getTotalEntradas();
        String idempotencyKey = dto.getIdempotencyKey();

        idempotencyService.registrarSolicitud(idempotencyKey, dto.getRequestHash());

        ReservaRequestDto reservaRequest = new ReservaRequestDto();
        reservaRequest.setCantidadEntradas(totalEntradas);
        reservaRequest.setIdUsuario(usuarioId);

        Long reservaId = null;
        Long eventoId = null;
        for (DetalleCarrito detalle : carrito.getDetalles()) {
            try {
                eventoClient.crearReserva(detalle.getEventoId(), detalle.getCantidad());
                reservaId = 0L;
                eventoId = detalle.getEventoId();
                detalle.setIdReserva(reservaId);
            } catch (RuntimeException e) {
                log.error("==== ERROR EN FEIGN AL CREAR RESERVA ====", e);
                if (reservaId != null) {
                    try {
                        eventoClient.liberarReserva(detalle.getEventoId(), detalle.getCantidad());
                    } catch (RuntimeException ex) {
                        log.error("Error liberando reserva: {}", ex.getMessage());
                    }
                }
                idempotencyService.marcarFallido(idempotencyKey);
                throw CarritoException.stockNoDisponible(detalle.getEventoId());
            }
        }

        Reserva reserva = new Reserva();
        reserva.setFechaReserva(LocalDateTime.now());
        reserva.setUsuarioIdUsu(usuarioId);
        reserva.setRolUsuarioIdUsuRol(carrito.getRolUsuarioId());
        reserva.setCarritoDeComprasIdCarrito(carritoId);
        reserva.setEventoId(eventoId);
        reserva.setCantidadEntradas(totalEntradas);
        reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_INICIADA);
        reserva.setFechaExpiracion(LocalDateTime.now().plusMinutes(MINUTOS_RESERVA));
        reserva = reservaRepository.save(reserva);

        carrito.setCausaSocialId(dto.getCausaSocialId());
        carrito.recalcularTotales();
        carrito.setReservaId(reserva.getIdReserva());
        carrito.setIdempotencyKey(idempotencyKey);
        carrito.setEstadoCarrito(EstadoCarrito.RESERVADO);
        carrito.setFechaExpiracionReserva(LocalDateTime.now().plusMinutes(MINUTOS_RESERVA));
        carrito.setFechaUltActualizacion(LocalDateTime.now());

        detalleRepository.saveAll(carrito.getDetalles());

        CarritoDeCompras carritoGuardado = carritoRepository.save(carrito);

        // Procesar el pago automáticamente en la misma transacción (simulado para desarrollo)
        try {
            WebhookPagoDto pagoDto = new WebhookPagoDto();
            pagoDto.setPedidoId(carritoId);
            pagoDto.setEstado("aprobado");
            pagoDto.setToken("simulado-checkout");
            pagoDto.setTimestamp(LocalDateTime.now().toString());
            pagoDto.setNonce(java.util.UUID.randomUUID().toString());

            carritoGuardado = pagoWebhookService.procesarPagoAprobado(carritoGuardado, pagoDto);
        } catch (Exception e) {
            log.error("Error al procesar pago automático en checkout: {}", e.getMessage());
            // No lanzamos error porque el checkout ya se completó exitosamente
        }

        return carritoGuardado;
    }

    /**
     * Renueva la reserva del carrito por el tiempo configurado.
     */
    @Transactional
    public CarritoDeCompras renovarReserva(Long carritoId, Long usuarioId) {
        log.info("Renovando reserva del carrito {}", carritoId);

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        if (!carrito.puedeRenovarReserva()) {
            if (carrito.isRenovacionUsada()) {
                throw CarritoException.reservaYaRenovada();
            }
            throw CarritoException.reservaExpirada();
        }

        Reserva reserva = reservaRepository.findById(carrito.getReservaId()).orElse(null);
        if (reserva != null) {
            reserva.setFechaExpiracion(LocalDateTime.now().plusMinutes(MINUTOS_RENOVACION));
            reservaRepository.save(reserva);
        }

        carrito.setRenovacionUsada(true);
        carrito.setFechaExpiracionReserva(LocalDateTime.now().plusMinutes(MINUTOS_RENOVACION));
        carrito.setFechaUltActualizacion(LocalDateTime.now());

        return carritoRepository.save(carrito);
    }

    /**
     * Procesa el webhook de pago y delega la persistencia transaccional.
     */
    public CarritoDeCompras procesarWebhookPago(Long carritoId, WebhookPagoDto dto) {

        validarEntrada(dto);
        log.info("Procesando webhook de pago para carrito {}", carritoId);

        validarWebhook(dto);

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        if (carrito.getEstadoCarrito() == EstadoCarrito.PAGADO) {
            return carrito;
        }

        if (!"aprobado".equalsIgnoreCase(dto.getEstado())) {
            if (carrito.getReservaId() != null) {
                Reserva reserva = reservaRepository.findById(carrito.getReservaId()).orElse(null);
                if (reserva != null) {
                    try {
                        eventoClient.liberarReserva(reserva.getEventoId(), reserva.getCantidadEntradas());
                        reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CANCELADA);
                        reservaRepository.save(reserva);
                    } catch (Exception e) {
                        log.error("Error liberando reserva: {}", e.getMessage());
                    }
                }
            }

            return pagoWebhookService.procesarPagoRechazado(carrito);
        }

        return pagoWebhookService.procesarPagoAprobado(carrito, dto);
    }

    /**
     * Procesa un pago manual (simulado) para desarrollo/pruebas sin pasarela de pagos.
     * Este método es similar al webhook pero diseñado para ser llamado directamente por el frontend.
     *
     * @param carritoId ID del carrito a procesar.
     * @param usuarioId ID del usuario que realiza el pago.
     * @return Carrito actualizado a estado PAGADO.
     */
    @Transactional
    public CarritoDeCompras procesarPagoManual(Long carritoId, Long usuarioId) {
        log.info("Procesando pago manual para carrito {} - Usuario: {}", carritoId, usuarioId);

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        if (carrito.getEstadoCarrito() == EstadoCarrito.PAGADO) {
            log.info("Carrito {} ya está en estado PAGADO", carritoId);
            return carrito;
        }

        if (carrito.getEstadoCarrito() != EstadoCarrito.RESERVADO) {
            throw CarritoException.transicionEstadoInvalida(carrito.getEstadoCarrito().toString(), "PAGADO");
        }

        // Crear un WebhookPagoDto simulado para reutilizar la lógica existente
        WebhookPagoDto dto = new WebhookPagoDto();
        dto.setPedidoId(carritoId);
        dto.setEstado("aprobado");
        dto.setToken("simulado-manual");
        dto.setTimestamp(java.time.LocalDateTime.now().toString());
        dto.setNonce(java.util.UUID.randomUUID().toString());

        return pagoWebhookService.procesarPagoAprobado(carrito, dto);
    }

    /**
     * Procesa una devolución y calcula el monto reembolsable.
     */
    @Transactional
    public DevolucionResponseDto procesarDevolucion(Long carritoId, Long usuarioId, DevolucionRequestDto dto) {
        validarEntrada(dto);
        log.info("Procesando devolucion del carrito {}", carritoId);

        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        if (carrito.getEstadoCarrito() != EstadoCarrito.PAGADO) {
            throw CarritoException.devolucionNoPermitida("Solo se pueden reembolsar carritos pagados");
        }

        BigDecimal montoReembolso = carrito.getSubtotal().multiply(PORCENTAJE_REEMBOLSO);

        if (carrito.getReservaId() != null) {
            Reserva reserva = reservaRepository.findById(carrito.getReservaId()).orElse(null);
            if (reserva != null) {
                try {
                    eventoClient.liberarReserva(reserva.getEventoId(), reserva.getCantidadEntradas());
                    reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CANCELADA);
                    reservaRepository.save(reserva);
                } catch (Exception e) {
                    log.error("Error liberando reserva en devolucion: {}", e.getMessage());
                }
            }
        }

        carrito.setEstadoCarrito(EstadoCarrito.REEMBOLSADO);
        carrito.setEstadoPago(EstadoPago.REEMBOLSADO);
        carrito.setFechaUltActualizacion(LocalDateTime.now());
        carritoRepository.save(carrito);

        Pago pago = pagoRepository.findByCarritoIdCarrito(carritoId).orElse(null);
        if (pago != null) {
            pago.setEstadoPago(EstadoPago.REEMBOLSADO);
            pagoRepository.save(pago);
        }

        guardarEventoOutbox(carrito, "compra.revertida");

        return DevolucionResponseDto.builder()
                .pedidoId(carritoId)
                .estadoDevolucion("REEMBOLSADO")
                .montoTotal(carrito.getTotal())
                .montoDevolucion(montoReembolso)
                .montoDonacionNoReembolsable(carrito.getMontoDonacion())
                .mensaje("Devolucion procesada exitosamente. El 10% de donacion no es reembolsable.")
                .fechaProcesamiento(LocalDateTime.now())
                .build();
    }

    /**
     * Obtiene un carrito concreto validando propiedad.
     */
    @Transactional(readOnly = true)
    public CarritoDeCompras obtenerCarrito(Long carritoId, Long usuarioId) {
        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));
        validarPropiedadCarrito(carrito, usuarioId);
        return carrito;
    }

    /**
     * Busca el carrito activo del usuario.
     */
    @Transactional(readOnly = true)
    public CarritoDeCompras buscarCarritoActivo(Long usuarioId) {
        List<CarritoDeCompras> carritos = carritoRepository.findByUsuarioIdAndEstadoCarrito(
                usuarioId, EstadoCarrito.CREADO);
        if (carritos.isEmpty()) {
            return null;
        }
        return carritos.isEmpty() ? null : carritos.get(0);
    }

    /**
     * Lista todos los carritos del usuario.
     */
    @Transactional(readOnly = true)
    public List<CarritoDeCompras> listarCarritosPorUsuario(Long usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId);
    }

    /**
     * Obtiene el resumen calculado del carrito.
     */
    @Transactional(readOnly = true)
    public ResumenCarritoDto obtenerResumen(Long carritoId, Long usuarioId) {
        CarritoDeCompras carrito = carritoRepository.findById(carritoId)
                .orElseThrow(() -> CarritoException.carritoNoEncontrado(carritoId));

        validarPropiedadCarrito(carrito, usuarioId);

        return ResumenCarritoDto.fromCarrito(carrito);
    }

    /**
     * Verifica que el carrito pertenezca al usuario indicado.
     */
    private void validarPropiedadCarrito(CarritoDeCompras carrito, Long usuarioId) {
        if (!carrito.getUsuarioId().equals(usuarioId)) {
            throw CarritoException.accesoNoAutorizado();
        }
    }

    /**
     * Valida un DTO de entrada usando Bean Validation.
     */
    private void validarEntrada(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /**
     * Valida las reglas de negocio del webhook de pago.
     */
    private void validarWebhook(WebhookPagoDto dto) {
        if (dto.getTimestamp() == null || dto.getNonce() == null) {
            throw CarritoException.webhookInvalido("falta timestamp o nonce");
        }

        LocalDateTime timestamp = LocalDateTime.parse(dto.getTimestamp());
        if (timestamp.isBefore(LocalDateTime.now().minusMinutes(WEBHOOK_TOLERANCE_MINUTES)) ||
            timestamp.isAfter(LocalDateTime.now().plusMinutes(WEBHOOK_TOLERANCE_MINUTES))) {
            throw CarritoException.webhookInvalido("timestamp fuera de rango");
        }
    }

    /**
     * Persiste un evento en la tabla outbox.
     */
    private void guardarEventoOutbox(CarritoDeCompras carrito, String tipo) {
        try {
            CompraConfirmadaEvent evento = construirEventoConfirmacion(carrito);
            String payload = objectMapper.writeValueAsString(evento);

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

    private CompraConfirmadaEvent construirEventoConfirmacion(CarritoDeCompras carrito) {
        CompraConfirmadaEvent evt = new CompraConfirmadaEvent();
        evt.setIdCarrito(carrito.getIdCarrito());
        evt.setPagoId(carrito.getIdPago());
        evt.setUsuarioId(carrito.getUsuarioId());
        evt.setCausaSocialId(carrito.getCausaSocialId());
        evt.setTotal(carrito.getTotal());
        evt.setMontoDonacion(carrito.getMontoDonacion());
        // intentar obtener eventoId desde el primer detalle del carrito si existe
        if (carrito.getDetalles() != null && !carrito.getDetalles().isEmpty()) {
            evt.setEventoId(carrito.getDetalles().get(0).getEventoId().longValue());
        } else {
            evt.setEventoId(null);
        }

        try {
            if (carrito.getUsuarioId() != null) {
                UsuarioInfoDto usuario = usuarioClient.buscarUsuario(carrito.getUsuarioId());
                if (usuario != null) {
                    evt.setCorreoUsuario(usuario.getCorreo());
                    evt.setNombreUsuario(usuario.getNombre());
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener usuario para enriquecimiento: {}", ex.getMessage());
        }

        try {
            Long eventoId = null;
            if (carrito.getDetalles() != null && !carrito.getDetalles().isEmpty()) {
                eventoId = carrito.getDetalles().get(0).getEventoId();
            } else if (carrito.getReservaId() != null) {
                eventoId = carrito.getReservaId();
            }

            if (eventoId != null) {
                EventoInfoDto evento = eventoClient.buscarEvento(eventoId.intValue());
                if (evento != null) {
                    evt.setNombreEvento(evento.getNombre());
                    evt.setFechaEvento(evento.getFecha() != null ? evento.getFecha().toString() : null);
                    if (evento.getRecinto() != null) {
                        evt.setLugarEvento(evento.getRecinto().getNombre());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener evento para enriquecimiento: {}", ex.getMessage());
        }

        try {
            if (carrito.getCausaSocialId() != null) {
                CausaSocialInfoDto causa = causaSocialClient.buscarCausa(carrito.getCausaSocialId());
                if (causa != null) {
                    evt.setNombreCausa(causa.getNombre());
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo obtener causa social para enriquecimiento: {}", ex.getMessage());
        }

        return evt;
    }
}
