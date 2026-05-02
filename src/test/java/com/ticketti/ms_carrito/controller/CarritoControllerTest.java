package com.ticketti.ms_carrito.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.dto.*;
import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.model.CarritoDeCompras;
import com.ticketti.ms_carrito.model.EstadoCarrito;
import com.ticketti.ms_carrito.model.EstadoPago;
import com.ticketti.ms_carrito.service.CarritoService;

@WebMvcTest(CarritoController.class)
@AutoConfigureMockMvc(addFilters = false)
class CarritoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarritoService carritoService;

    @Autowired
    private ObjectMapper objectMapper;

    private CarritoDeCompras carritoMock;
    private static final Long USUARIO_ID = 1L;
    private static final Long ROL_USUARIO_ID = 2L;
    private static final Long CARRITO_ID = 1L;

    @BeforeEach
    void setUp() {
        carritoMock = new CarritoDeCompras();
        carritoMock.setIdCarrito(CARRITO_ID);
        carritoMock.setUsuarioId(USUARIO_ID);
        carritoMock.setRolUsuarioId(ROL_USUARIO_ID);
                carritoMock.setEstadoCarrito(EstadoCarrito.CREADO);
                carritoMock.setEstadoPago(EstadoPago.PENDIENTE);
        carritoMock.setSubtotal(BigDecimal.ZERO);
        carritoMock.setMontoDonacion(BigDecimal.ZERO);
        carritoMock.setTotal(BigDecimal.ZERO);
    }

    @Test
    void crearCarrito_DebeRetornar201() throws Exception {
        when(carritoService.crearCarrito(USUARIO_ID, ROL_USUARIO_ID)).thenReturn(carritoMock);

        mockMvc.perform(post("/api/v1/Carrito/crear")
                .header("X-Usuario-Id", USUARIO_ID)
                .header("X-Rol-Usuario-Id", ROL_USUARIO_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.idCarrito").value(CARRITO_ID));
    }

    @Test
    void listarCarritos_DebeRetornar200() throws Exception {
        when(carritoService.listarCarritosPorUsuario(USUARIO_ID)).thenReturn(List.of(carritoMock));

        mockMvc.perform(get("/api/v1/Carrito/listar")
                .header("X-Usuario-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data[0].idCarrito").value(CARRITO_ID));
    }

    @Test
    void listarCarritos_DebeRetornar204_CuandoVacio() throws Exception {
        when(carritoService.listarCarritosPorUsuario(USUARIO_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/Carrito/listar")
                .header("X-Usuario-Id", USUARIO_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void agregarEntrada_DebeRetornar200() throws Exception {
        AgregarEntradaDto dto = new AgregarEntradaDto(1L, "General", 2, new BigDecimal("10000"));
        when(carritoService.agregarEntrada(eq(CARRITO_ID), eq(USUARIO_ID), any(AgregarEntradaDto.class)))
                .thenReturn(carritoMock);

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", CARRITO_ID)
                .header("X-Usuario-Id", USUARIO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void agregarEntrada_DebeRetornar400_CuandoLimiteExcedido() throws Exception {
        AgregarEntradaDto dto = new AgregarEntradaDto(1L, "General", 5, new BigDecimal("10000"));
        when(carritoService.agregarEntrada(eq(CARRITO_ID), eq(USUARIO_ID), any(AgregarEntradaDto.class)))
                .thenThrow(new CarritoException("Maximo 4 entradas por compra"));

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", CARRITO_ID)
                .header("X-Usuario-Id", USUARIO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void iniciarCheckout_DebeRetornar200() throws Exception {
        CheckoutDto dto = new CheckoutDto(1L, "idempotency-key-test", "token-test", null);
        when(carritoService.iniciarCheckout(eq(CARRITO_ID), eq(USUARIO_ID), any(CheckoutDto.class)))
                .thenReturn(carritoMock);

        mockMvc.perform(post("/api/v1/Carrito/checkout/{id}", CARRITO_ID)
                .header("X-Usuario-Id", USUARIO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void procesarDevolucion_DebeRetornar200() throws Exception {
        DevolucionRequestDto dto = new DevolucionRequestDto(CARRITO_ID, "No puedo asistir");
        DevolucionResponseDto respuesta = DevolucionResponseDto.builder()
                .pedidoId(CARRITO_ID)
                .estadoDevolucion("REEMBOLSADO")
                .montoTotal(new BigDecimal("11000"))
                .montoDevolucion(new BigDecimal("8500"))
                .montoDonacionNoReembolsable(new BigDecimal("1000"))
                .build();

        when(carritoService.procesarDevolucion(eq(CARRITO_ID), eq(USUARIO_ID), any(DevolucionRequestDto.class)))
                .thenReturn(respuesta);

        mockMvc.perform(post("/api/v1/Carrito/devoluciones/{id}", CARRITO_ID)
                .header("X-Usuario-Id", USUARIO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.estadoDevolucion").value("REEMBOLSADO"));
    }

    @Test
    void vaciarCarrito_DebeRetornar200() throws Exception {
        mockMvc.perform(delete("/api/v1/Carrito/vaciar")
                .header("X-Carrito-Id", CARRITO_ID)
                .header("X-Usuario-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void eliminarEntrada_DebeRetornar404_CuandoDetalleNoExiste() throws Exception {
        Long detalleId = 999L;
        when(carritoService.eliminarEntrada(eq(CARRITO_ID), eq(detalleId), eq(USUARIO_ID)))
                .thenThrow(new CarritoException("Detalle no encontrado"));

        mockMvc.perform(delete("/api/v1/Carrito/{id}/entradas/{detalleId}", CARRITO_ID, detalleId)
                .header("X-Usuario-Id", USUARIO_ID))
                                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void actualizarCarrito_DebeRetornar200() throws Exception {
        AgregarEntradaDto dto = new AgregarEntradaDto(1L, "VIP", 1, new BigDecimal("20000"));
        when(carritoService.agregarEntrada(eq(CARRITO_ID), eq(USUARIO_ID), any(AgregarEntradaDto.class)))
                .thenReturn(carritoMock);

        mockMvc.perform(put("/api/v1/Carrito/actualizar/{id}", CARRITO_ID)
                .header("X-Usuario-Id", USUARIO_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }
}
