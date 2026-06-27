package com.ticketti.ms_carrito.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.AmqpException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.messaging.CompraConfirmadaEvent;
import com.ticketti.ms_carrito.model.OutboxEvent;
import com.ticketti.ms_carrito.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxCaptor;

    private CompraConfirmadaEvent compraEvent;
    private static final Long CARRITO_ID = 1L;
    private static final String ROUTING_KEY = "pago.aprobado";
    private static final String TIPO_EVENTO = "pago.aprobado";
    private static final String PAYLOAD_JSON = "{\"idCarrito\":1}";

    @BeforeEach
    void setUp() throws Exception {
        compraEvent = new CompraConfirmadaEvent();
        compraEvent.setIdCarrito(CARRITO_ID);
        compraEvent.setTotal(new BigDecimal("11000"));

        when(objectMapper.readValue(anyString(), eq(CompraConfirmadaEvent.class)))
                .thenReturn(compraEvent);
    }

    private OutboxEvent crearEventoPendiente(Long id, String tipo, String routingKey,
                                              LocalDateTime createdAt, int retryCount) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateId(CARRITO_ID);
        event.setType(tipo);
        event.setRoutingKey(routingKey);
        event.setPayload(PAYLOAD_JSON);
        event.setStatus(OutboxEvent.Status.PENDING);
        event.setCreatedAt(createdAt);
        event.setRetryCount(retryCount);
        return event;
    }

    private OutboxEvent crearEventoPendiente(Long id, LocalDateTime createdAt) {
        return crearEventoPendiente(id, TIPO_EVENTO, ROUTING_KEY, createdAt, 0);
    }

    private OutboxEvent crearEventoFallido(Long id, LocalDateTime createdAt, int retryCount) {
        OutboxEvent event = crearEventoPendiente(id, TIPO_EVENTO, ROUTING_KEY, createdAt, retryCount);
        event.setStatus(OutboxEvent.Status.FAILED);
        return event;
    }

    @Test
    void publicarEventosPendientes_DebePublicarYMarcarEnviados() throws Exception {
        OutboxEvent event = crearEventoPendiente(1L, LocalDateTime.now().minusSeconds(10));

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of(event));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate).convertAndSend("ticketti.exchange", ROUTING_KEY, compraEvent);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals(OutboxEvent.Status.SENT, outboxCaptor.getValue().getStatus());
        assertNotNull(outboxCaptor.getValue().getSentAt());
    }

    @Test
    void publicarEventosPendientes_DebeIncluirEventosFallidosParaReintento() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        OutboxEvent pending = crearEventoPendiente(1L, ahora.minusSeconds(10));
        OutboxEvent fallido = crearEventoFallido(2L, ahora.minusSeconds(20), 1);

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of(pending));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of(fallido));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publicarEventosPendientes_DebeSaltarEventosFallidosFueraDeBackoff() {
        // retryCount=0, backoff=5s, createdAt=3s ago → backoff no ha expirado
        OutboxEvent fallido = crearEventoFallido(1L, LocalDateTime.now().minusSeconds(3), 0);

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of());
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of(fallido));

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publicarEventosPendientes_DebeSaltarEventosSinReintentosRestantes() {
        OutboxEvent fallido = crearEventoFallido(1L, LocalDateTime.now().minusSeconds(60), 5);

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of());
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of(fallido));

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publicarEventosPendientes_DebeUsarFallbackPayload_CuandoDeserializacionFalla() throws Exception {
        OutboxEvent event = crearEventoPendiente(1L, LocalDateTime.now().minusSeconds(10));

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of(event));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        when(objectMapper.readValue(anyString(), eq(CompraConfirmadaEvent.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("error") {});
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate).convertAndSend("ticketti.exchange", ROUTING_KEY, PAYLOAD_JSON);
    }

    @Test
    void publicarEventosPendientes_DebeIncrementarRetry_CuandoFallaPublicacion() {
        OutboxEvent event = crearEventoPendiente(1L, LocalDateTime.now().minusSeconds(10));

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of(event));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        doThrow(new AmqpException("RabbitMQ caido"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayService.publicarEventosPendientes();

        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();
        assertEquals(OutboxEvent.Status.FAILED, saved.getStatus());
        assertEquals(1, saved.getRetryCount());
    }

    @Test
    void publicarEventosPendientes_DebeLimitarLoteAMaximo() throws Exception {
        LocalDateTime fijo = LocalDateTime.now();
        List<OutboxEvent> muchos = IntStream.range(0, 60)
                .mapToObj(i -> crearEventoPendiente((long) i, fijo.minusSeconds(60)))
                .toList();

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(muchos);
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate, times(50)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publicarEventosPendientes_DebeOrdenarPorCreatedAt() throws Exception {
        LocalDateTime fijo = LocalDateTime.now();
        OutboxEvent viejo = crearEventoPendiente(1L, fijo.minusSeconds(30));
        OutboxEvent medio = crearEventoPendiente(2L, fijo.minusSeconds(20));
        OutboxEvent reciente = crearEventoPendiente(3L, fijo.minusSeconds(10));

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(reciente, viejo, medio));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate, times(3))
                .convertAndSend(anyString(), anyString(), payloadCaptor.capture());

        assertEquals(viejo.getId(), 1L);
        assertEquals(medio.getId(), 2L);
        assertEquals(reciente.getId(), 3L);
    }

    @Test
    void publicarEventosPendientes_NoDebeHacerNada_CuandoNoHayPendientes() {
        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of());
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void publicarEventosPendientes_DebeUsarRoutingKeyDelEvento() throws Exception {
        String routingKeyCustom = "pago.rechazado";
        OutboxEvent event = crearEventoPendiente(1L, TIPO_EVENTO, routingKeyCustom,
                LocalDateTime.now().minusSeconds(10), 0);

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of(event));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate).convertAndSend("ticketti.exchange", routingKeyCustom, compraEvent);
    }

    @Test
    void publicarEventosPendientes_DebeUsarTypeCuandoRoutingKeyEsNull() throws Exception {
        OutboxEvent event = crearEventoPendiente(1L, "compra.revertida", null,
                LocalDateTime.now().minusSeconds(10), 0);

        when(outboxRepository.findByStatus(OutboxEvent.Status.PENDING)).thenReturn(List.of(event));
        when(outboxRepository.findByStatus(OutboxEvent.Status.FAILED)).thenReturn(List.of());
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        outboxRelayService.publicarEventosPendientes();

        verify(rabbitTemplate).convertAndSend("ticketti.exchange", "compra.revertida", compraEvent);
    }
}
