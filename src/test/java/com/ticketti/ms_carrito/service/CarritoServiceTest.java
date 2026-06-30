package com.ticketti.ms_carrito.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.dto.AgregarEntradaDto;
import com.ticketti.ms_carrito.dto.CheckoutDto;
import com.ticketti.ms_carrito.dto.DevolucionRequestDto;
import com.ticketti.ms_carrito.dto.DevolucionRequestDto;
import com.ticketti.ms_carrito.dto.DevolucionResponseDto;
import com.ticketti.ms_carrito.dto.ResumenCarritoDto;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.DetalleCarrito;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.model.IdempotencyRecord;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.model.Reserva;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.DetalleCarritoRepository;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;
import com.ticketti.ms_carrito.repository.PagoRepository;
import com.ticketti.ms_carrito.repository.ReservaRepository;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unused")
class CarritoServiceTest {

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private DetalleCarritoRepository detalleRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NonceService nonceService;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private EventoClient eventoClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CarritoService carritoService;

    private CarritoDeCompras carrito;
    private static final Long USUARIO_ID = 1L;
    private static final String ROL_USUARIO = "CLIENTE";
    private static final Long CARRITO_ID = 1L;
    private static final Long EVENTO_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        carrito = new CarritoDeCompras();
        carrito.setIdCarrito(CARRITO_ID);
        carrito.setUsuarioId(USUARIO_ID);
        carrito.setRolUsuarioId(0L);
        carrito.setEstadoCarrito(EstadoCarrito.CREADO);
        carrito.setEstadoPago(EstadoPago.PENDIENTE);
        carrito.setSubtotal(BigDecimal.ZERO);
        carrito.setMontoDonacion(BigDecimal.ZERO);
        carrito.setTotal(BigDecimal.ZERO);
        carrito.setDetalles(new ArrayList<>());
        carrito.setRenovacionUsada(false);
    }

    @Test
    void crearCarrito_DebeRetornarCarritoConEstadoCreado() {
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        CarritoDeCompras resultado = carritoService.crearCarrito(USUARIO_ID, ROL_USUARIO);

        assertNotNull(resultado);
        assertEquals(USUARIO_ID, resultado.getUsuarioId());
        assertEquals(0L, resultado.getRolUsuarioId());
        assertEquals(EstadoCarrito.CREADO, resultado.getEstadoCarrito());
        assertEquals(EstadoPago.PENDIENTE, resultado.getEstadoPago());
    }

    @Test
    void agregarEntrada_DebeAgregarDetalleYRecalcularTotales() {
        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        AgregarEntradaDto dto = new AgregarEntradaDto(EVENTO_ID, "General", 2, new BigDecimal("10000"));

        CarritoDeCompras resultado = carritoService.agregarEntrada(CARRITO_ID, USUARIO_ID, dto);

        assertNotNull(resultado);
        assertEquals(1, resultado.getDetalles().size());
        assertEquals(2, resultado.getTotalEntradas());
        assertEquals(new BigDecimal("20000"), resultado.getSubtotal());
        assertEquals(0, resultado.getMontoDonacion().compareTo(new BigDecimal("2000")));
    }

    @Test
    void agregarEntrada_DebeLanzarExcepcion_CuandoExcedeLimiteDe4Entradas() {
        DetalleCarrito detalle1 = new DetalleCarrito();
        detalle1.setEventoId(EVENTO_ID);
        detalle1.setTipoEntradaNombre("General");
        detalle1.setCantidad(3);
        detalle1.setPrecioUnitario(new BigDecimal("10000"));
        detalle1.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle1);
        carrito.recalcularTotales();

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));

        AgregarEntradaDto dto = new AgregarEntradaDto(EVENTO_ID, "VIP", 2, new BigDecimal("20000"));

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.agregarEntrada(CARRITO_ID, USUARIO_ID, dto));
        assertNotNull(exception);
    }

    @Test
    void iniciarCheckout_DebeReservarStockYGenerarIdempotenciaKey() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("10000"));
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);
        carrito.recalcularTotales();

        Reserva reservaGuardada = new Reserva();
        reservaGuardada.setIdReserva(100L);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        doNothing().when(eventoClient).crearReserva(any(), any());
        when(idempotencyService.registrarSolicitud(eq("idempotency-key-test-32-chars-long-valid"), any()))
                .thenReturn(new IdempotencyRecord());
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaGuardada);
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        CheckoutDto dto = new CheckoutDto(1L, "idempotency-key-test-32-chars-long-valid", "token-test", null);
        CarritoDeCompras resultado = carritoService.iniciarCheckout(CARRITO_ID, USUARIO_ID, dto);

        assertNotNull(resultado);
        assertEquals(EstadoCarrito.RESERVADO, resultado.getEstadoCarrito());
        assertNotNull(resultado.getReservaId());
        assertEquals("idempotency-key-test-32-chars-long-valid", resultado.getIdempotencyKey());
    }

    @Test
    void iniciarCheckout_DebeLanzarExcepcion_CuandoNoHayStock() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setCantidad(2);
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        doThrow(FeignException.class).when(eventoClient).crearReserva(any(), any());
        when(idempotencyService.registrarSolicitud(eq("idempotency-key-456-789012345678901234"), any()))
                .thenReturn(new IdempotencyRecord());

        CheckoutDto dto = new CheckoutDto(1L, "idempotency-key-456-789012345678901234", "token-456", null);

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.iniciarCheckout(CARRITO_ID, USUARIO_ID, dto));
        assertNotNull(exception);
    }

    @Test
    void iniciarCheckout_DebeRetornarCarritoExistente_CuandoClaveDeIdempotenciaEstaCompleted() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setCantidad(2);
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);
        carrito.setEstadoCarrito(EstadoCarrito.RESERVADO);
        carrito.setIdempotencyKey("idempotency-key-test-completed-32-chars");

        IdempotencyRecord record = new IdempotencyRecord();
        record.setKey("idempotency-key-test-completed-32-chars");
        record.setStatus(IdempotencyRecord.Status.COMPLETED);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(idempotencyService.obtenerRecord("idempotency-key-test-completed-32-chars")).thenReturn(Optional.of(record));
        when(carritoRepository.findByIdempotencyKey("idempotency-key-test-completed-32-chars")).thenReturn(Optional.of(carrito));

        CheckoutDto dto = new CheckoutDto(1L, "idempotency-key-test-completed-32-chars", "token-test", null);
        CarritoDeCompras resultado = carritoService.iniciarCheckout(CARRITO_ID, USUARIO_ID, dto);

        assertNotNull(resultado);
        assertEquals("idempotency-key-test-completed-32-chars", resultado.getIdempotencyKey());
    }

    @Test
    void iniciarCheckout_DebeLanzarExcepcion_CuandoClaveDeIdempotenciaEstaPending() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setCantidad(2);
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setKey("idempotency-key-test-pending-32-chars");
        record.setStatus(IdempotencyRecord.Status.PENDING);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(idempotencyService.obtenerRecord("idempotency-key-test-pending-32-chars")).thenReturn(Optional.of(record));

        CheckoutDto dto = new CheckoutDto(1L, "idempotency-key-test-pending-32-chars", "token-test", null);

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.iniciarCheckout(CARRITO_ID, USUARIO_ID, dto));
        assertNotNull(exception);
        assertEquals(CarritoException.CodigoError.IDEMPOTENCIA_INVALIDA, exception.getCodigo());
        assertTrue(exception.getMessage().contains("Solicitud en proceso"));
    }

    @Test
    void iniciarCheckout_DebeEliminarRecordYProceder_CuandoClaveDeIdempotenciaEstaFailed() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("10000"));
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);
        carrito.recalcularTotales();

        IdempotencyRecord record = new IdempotencyRecord();
        record.setKey("idempotency-key-test-failed-32-chars");
        record.setStatus(IdempotencyRecord.Status.FAILED);

        Reserva reservaGuardada = new Reserva();
        reservaGuardada.setIdReserva(100L);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(idempotencyService.obtenerRecord("idempotency-key-test-failed-32-chars")).thenReturn(Optional.of(record));
        doNothing().when(idempotencyService).eliminarRecord("idempotency-key-test-failed-32-chars");
        doNothing().when(eventoClient).crearReserva(any(), any());
        when(idempotencyService.registrarSolicitud(eq("idempotency-key-test-failed-32-chars"), any()))
                .thenReturn(new IdempotencyRecord());
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reservaGuardada);
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        CheckoutDto dto = new CheckoutDto(1L, "idempotency-key-test-failed-32-chars", "token-test", null);
        CarritoDeCompras resultado = carritoService.iniciarCheckout(CARRITO_ID, USUARIO_ID, dto);

        assertNotNull(resultado);
        assertEquals(EstadoCarrito.RESERVADO, resultado.getEstadoCarrito());
        assertEquals("idempotency-key-test-failed-32-chars", resultado.getIdempotencyKey());
    }

    @Test
    void renovarReserva_DebeExtenderExpiracion_CuandoEsPrimeraVez() {
        carrito.setEstadoCarrito(EstadoCarrito.RESERVADO);
        carrito.setFechaExpiracionReserva(LocalDateTime.now().plusMinutes(3));
        carrito.setRenovacionUsada(false);
        carrito.setReservaId(100L);

        Reserva reserva = new Reserva();
        reserva.setIdReserva(100L);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(reservaRepository.findById(100L)).thenReturn(Optional.of(reserva));
        when(reservaRepository.save(any(Reserva.class))).thenReturn(reserva);
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        CarritoDeCompras resultado = carritoService.renovarReserva(CARRITO_ID, USUARIO_ID);

        assertTrue(resultado.isRenovacionUsada());
    }

    @Test
    void renovarReserva_DebeLanzarExcepcion_CuandoYaFueRenovada() {
        carrito.setEstadoCarrito(EstadoCarrito.RESERVADO);
        carrito.setFechaExpiracionReserva(LocalDateTime.now().plusMinutes(3));
        carrito.setRenovacionUsada(true);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.renovarReserva(CARRITO_ID, USUARIO_ID));
        assertNotNull(exception);
    }

    @Test
    void procesarDevolucion_DebeCalcular85PorcientoReembolso() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("10000"));
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);
        carrito.setEstadoCarrito(EstadoCarrito.PAGADO);
        carrito.setEstadoPago(EstadoPago.PAGADO);
        carrito.setReservaId(100L);
        carrito.recalcularTotales();

        Reserva reserva = new Reserva();
        reserva.setIdReserva(100L);
        reserva.setEventoId(EVENTO_ID);
        reserva.setCantidadEntradas(2);
        reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_CONFIRMADA);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(reservaRepository.findByCarritoDeComprasIdCarrito(CARRITO_ID)).thenReturn(java.util.List.of(reserva));
        doNothing().when(eventoClient).liberarReserva(any(), any());
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);
        when(outboxRepository.save(any())).thenReturn(new OutboxEvent());

        DevolucionRequestDto dto = new DevolucionRequestDto(CARRITO_ID, "No puedo asistir");
        DevolucionResponseDto resultado = carritoService.procesarDevolucion(CARRITO_ID, USUARIO_ID, dto);

        assertNotNull(resultado);
        assertEquals(0, resultado.getMontoDevolucion().compareTo(new BigDecimal("17000")));
        assertEquals(0, resultado.getMontoDonacionNoReembolsable().compareTo(new BigDecimal("2000")));
    }

    @Test
    void procesarDevolucion_DebeLanzarExcepcion_CuandoCarritoNoEstaPagado() {
        carrito.setEstadoCarrito(EstadoCarrito.CREADO);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));

        DevolucionRequestDto dto = new DevolucionRequestDto(CARRITO_ID, "No puedo asistir");

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.procesarDevolucion(CARRITO_ID, USUARIO_ID, dto));
        assertNotNull(exception);
    }

    @Test
    void vaciarCarrito_DebeCancelarCarritoYLiberarReservas() {
        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);
        carrito.setEstadoCarrito(EstadoCarrito.RESERVADO);
        carrito.setReservaId(100L);

        Reserva reserva = new Reserva();
        reserva.setIdReserva(100L);
        reserva.setEventoId(EVENTO_ID);
        reserva.setCantidadEntradas(1);
        reserva.setEstadoReserva(Reserva.EstadoReserva.RESERVA_INICIADA);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));
        when(reservaRepository.findByCarritoDeComprasIdCarrito(CARRITO_ID)).thenReturn(java.util.List.of(reserva));
        doNothing().when(detalleRepository).deleteByIdCarritoDeCompras(CARRITO_ID);
        doNothing().when(eventoClient).liberarReserva(any(), any());
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        CarritoDeCompras resultado = carritoService.vaciarCarrito(CARRITO_ID, USUARIO_ID);

        assertEquals(EstadoCarrito.CANCELADO, resultado.getEstadoCarrito());
        assertTrue(resultado.getDetalles().isEmpty());
    }

    @Test
    void obtenerVenta_DebeRetornarResumen_CuandoEstadoEsPagado() {
        carrito.setEstadoCarrito(EstadoCarrito.PAGADO);
        carrito.setEstadoPago(EstadoPago.PAGADO);

        DetalleCarrito detalle = new DetalleCarrito();
        detalle.setEventoId(EVENTO_ID);
        detalle.setTipoEntradaNombre("General");
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(new BigDecimal("10000"));
        detalle.setIdCarritoDeCompras(CARRITO_ID);
        carrito.getDetalles().add(detalle);
        carrito.recalcularTotales();

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));

        ResumenCarritoDto resultado = carritoService.obtenerVenta(CARRITO_ID);

        assertNotNull(resultado);
        assertEquals(CARRITO_ID, resultado.getCarritoId());
        assertEquals("PAGADO", resultado.getEstadoCarrito());
        assertEquals("PAGADO", resultado.getEstadoPago());
        assertEquals(1, resultado.getItems().size());
        assertEquals("General", resultado.getItems().get(0).getTipoEntrada());
        assertEquals(2, resultado.getTotalEntradas());
    }

    @Test
    void obtenerVenta_DebeRetornarResumen_CuandoEstadoEsReembolsado() {
        carrito.setEstadoCarrito(EstadoCarrito.REEMBOLSADO);
        carrito.setEstadoPago(EstadoPago.REEMBOLSADO);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));

        ResumenCarritoDto resultado = carritoService.obtenerVenta(CARRITO_ID);

        assertNotNull(resultado);
        assertEquals(CARRITO_ID, resultado.getCarritoId());
        assertEquals("REEMBOLSADO", resultado.getEstadoCarrito());
    }

    @Test
    void obtenerVenta_DebeLanzarExcepcion_CuandoNoExiste() {
        when(carritoRepository.findById(999L)).thenReturn(Optional.empty());

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.obtenerVenta(999L));
        assertNotNull(exception);
        assertEquals(CarritoException.CodigoError.CARRO_NO_ENCONTRADO, exception.getCodigo());
    }

    @Test
    void obtenerVenta_DebeLanzarExcepcion_CuandoEstadoEsCreado() {
        carrito.setEstadoCarrito(EstadoCarrito.CREADO);

        when(carritoRepository.findById(CARRITO_ID)).thenReturn(Optional.of(carrito));

        CarritoException exception = assertThrows(CarritoException.class,
            () -> carritoService.obtenerVenta(CARRITO_ID));
        assertNotNull(exception);
        assertEquals(CarritoException.CodigoError.CARRO_NO_ENCONTRADO, exception.getCodigo());
        assertTrue(exception.getMessage().contains("no se encuentra en estado válido"));
    }
}
