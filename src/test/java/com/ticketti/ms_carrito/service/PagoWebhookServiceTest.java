package com.ticketti.ms_carrito.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.dto.WebhookPagoDto;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.model.Pago;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;
import com.ticketti.ms_carrito.repository.PagoRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PagoWebhookServiceTest {

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CarritoService carritoService;

    @InjectMocks
    private PagoWebhookService pagoWebhookService;

    private CarritoDeCompras carrito;
    private WebhookPagoDto dto;
    private static final Long CARRITO_ID = 1L;
    private static final Long USUARIO_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key-test-32-chars-long-valid";

    @BeforeEach
    void setUp() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        carrito = new CarritoDeCompras();
        carrito.setIdCarrito(CARRITO_ID);
        carrito.setUsuarioId(USUARIO_ID);
        carrito.setEstadoCarrito(EstadoCarrito.RESERVADO);
        carrito.setEstadoPago(EstadoPago.PENDIENTE);
        carrito.setSubtotal(new BigDecimal("10000"));
        carrito.setMontoDonacion(new BigDecimal("1000"));
        carrito.setTotal(new BigDecimal("11000"));
        carrito.setReservaId(100L);
        carrito.setIdempotencyKey(IDEMPOTENCY_KEY);

        dto = new WebhookPagoDto(CARRITO_ID, "APROBADO", "token-abc-123", "2025-01-01T00:00:00Z", "nonce-001");
    }

    @Test
    void procesarPagoAprobado_DebeActualizarEstadoYGuardarPagoYOutbox() {
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);
        when(pagoRepository.save(any(Pago.class))).thenReturn(new Pago());
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());
        when(idempotencyService.marcarCompletado(anyString(), anyString()))
                .thenReturn(new com.ticketti.ms_carrito.model.IdempotencyRecord());
        when(carritoService.crearCarrito(USUARIO_ID, "CLIENTE"))
                .thenReturn(new CarritoDeCompras());

        CarritoDeCompras resultado = pagoWebhookService.procesarPagoAprobado(carrito, dto);

        assertNotNull(resultado);
        assertEquals(EstadoCarrito.PAGADO, resultado.getEstadoCarrito());
        assertEquals(EstadoPago.PAGADO, resultado.getEstadoPago());
        assertNotNull(resultado.getFechaUltActualizacion());

        verify(carritoRepository).save(carrito);
        verify(pagoRepository).save(any(Pago.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
        verify(idempotencyService).marcarCompletado(eq(IDEMPOTENCY_KEY), anyString());
        verify(carritoService).crearCarrito(USUARIO_ID, "CLIENTE");
    }

    @Test
    void procesarPagoAprobado_DebeMarcarIdempotenciaSoloCuandoExisteKey() {
        carrito.setIdempotencyKey(null);

        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);
        when(pagoRepository.save(any(Pago.class))).thenReturn(new Pago());
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());
        when(carritoService.crearCarrito(USUARIO_ID, "CLIENTE"))
                .thenReturn(new CarritoDeCompras());

        pagoWebhookService.procesarPagoAprobado(carrito, dto);

        verify(idempotencyService, never()).marcarCompletado(anyString(), anyString());
    }

    @Test
    void procesarPagoAprobado_DebeLoguearErrorSinInterrumpirFlujo_CuandoFallaNuevoCarrito() {
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);
        when(pagoRepository.save(any(Pago.class))).thenReturn(new Pago());
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());
        when(idempotencyService.marcarCompletado(anyString(), anyString()))
                .thenReturn(new com.ticketti.ms_carrito.model.IdempotencyRecord());
        when(carritoService.crearCarrito(USUARIO_ID, "CLIENTE"))
                .thenThrow(new RuntimeException("Error de conexion"));

        CarritoDeCompras resultado = pagoWebhookService.procesarPagoAprobado(carrito, dto);

        assertNotNull(resultado);
        assertEquals(EstadoCarrito.PAGADO, resultado.getEstadoCarrito());
    }

    @Test
    void procesarPagoAprobado_DebeLanzarExcepcion_CuandoErrorSerializacionOutbox() throws Exception {
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);
        when(pagoRepository.save(any(Pago.class))).thenReturn(new Pago());
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Error de serializacion") {});

        assertThrows(CarritoException.class,
                () -> pagoWebhookService.procesarPagoAprobado(carrito, dto));
    }

    @Test
    void procesarPagoRechazado_DebeActualizarEstadoYMarcarIdempotenciaFallida() {
        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);
        when(idempotencyService.marcarFallido(IDEMPOTENCY_KEY))
                .thenReturn(new com.ticketti.ms_carrito.model.IdempotencyRecord());

        CarritoDeCompras resultado = pagoWebhookService.procesarPagoRechazado(carrito);

        assertNotNull(resultado);
        assertEquals(EstadoCarrito.FALLIDO, resultado.getEstadoCarrito());
        assertEquals(EstadoPago.FALLIDO, resultado.getEstadoPago());
        assertNotNull(resultado.getFechaUltActualizacion());

        verify(idempotencyService).marcarFallido(IDEMPOTENCY_KEY);
    }

    @Test
    void procesarPagoRechazado_DebeActualizarEstadoCuandoNoHayIdempotencyKey() {
        carrito.setIdempotencyKey(null);

        when(carritoRepository.save(any(CarritoDeCompras.class))).thenReturn(carrito);

        CarritoDeCompras resultado = pagoWebhookService.procesarPagoRechazado(carrito);

        assertNotNull(resultado);
        assertEquals(EstadoCarrito.FALLIDO, resultado.getEstadoCarrito());

        verify(idempotencyService, never()).marcarFallido(anyString());
    }
}
