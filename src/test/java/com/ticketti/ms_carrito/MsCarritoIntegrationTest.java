package com.ticketti.ms_carrito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketti.ms_carrito.client.CausaSocialClient;
import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.client.UsuarioClient;
import com.ticketti.ms_carrito.dto.AgregarEntradaDto;
import com.ticketti.ms_carrito.repository.CarritoRepository;
import com.ticketti.ms_carrito.repository.DetalleCarritoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@WebAppConfiguration
@Transactional
class MsCarritoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private DetalleCarritoRepository detalleCarritoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventoClient eventoClient;

    @MockitoBean
    private UsuarioClient usuarioClient;

    @MockitoBean
    private CausaSocialClient causaSocialClient;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private final AgregarEntradaDto entradaValida = new AgregarEntradaDto(
            1L, "General", 2, new BigDecimal("15000.00"));

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        carritoRepository.deleteAll();
        detalleCarritoRepository.deleteAll();
    }

    @AfterEach
    void limpiar() {
        detalleCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
    }

    private Long crearCarritoYRetornarId() throws Exception {
        var result = mockMvc.perform(post("/api/v1/Carrito/crear")
                        .header("X-Usuario-Id", "1")
                        .header("X-Rol-Usuario-Id", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data").path("idCarrito").asLong();
    }

    @Test
    void crearCarrito_persisteYRetorna201() throws Exception {
        mockMvc.perform(post("/api/v1/Carrito/crear")
                        .header("X-Usuario-Id", "1")
                        .header("X-Rol-Usuario-Id", "CLIENTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.idCarrito").exists())
                .andExpect(jsonPath("$.data.usuarioId").value(1))
                .andExpect(jsonPath("$.data.subtotal").value(0))
                .andExpect(jsonPath("$.data.estadoCarrito").value("CREADO"));

        assertThat(carritoRepository.count()).isEqualTo(1);
    }

    @Test
    void crearCarrito_sinHeaders_retorna201() throws Exception {
        mockMvc.perform(post("/api/v1/Carrito/crear")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    void obtenerCarrito_existente_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(get("/api/v1/Carrito/obtener/{id}", id)
                        .header("X-Usuario-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.idCarrito").value(id))
                .andExpect(jsonPath("$.data.estadoCarrito").value("CREADO"));
    }

    @Test
    void obtenerCarrito_inexistente_retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/Carrito/obtener/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").value("Carrito no encontrado: 9999"));
    }

    @Test
    void agregarEntrada_carritoValido_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.totalEntradas").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(30000.0));

        var carrito = carritoRepository.findById(id).orElseThrow();
        assertThat(carrito.getDetalles()).hasSize(1);
        assertThat(carrito.getTotalEntradas()).isEqualTo(2);
    }

    @Test
    void agregarEntrada_carritoInexistente_retorna404() throws Exception {
        mockMvc.perform(post("/api/v1/Carrito/9999/entradas")
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void agregarEntrada_sinBody_retornaError() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void agregarEntrada_limiteExcedido_retorna400() throws Exception {
        Long id = crearCarritoYRetornarId();
        var entrada5 = new AgregarEntradaDto(1L, "General", 5, new BigDecimal("15000.00"));

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entrada5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").value("Máximo 4 entradas por compra. Actualmente tiene: 0"));
    }

    @Test
    void eliminarEntrada_valida_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isOk());

        var carrito = carritoRepository.findById(id).orElseThrow();
        Long detalleId = carrito.getDetalles().get(0).getIdDetalleCarrito();

        mockMvc.perform(delete("/api/v1/Carrito/{id}/entradas/{detalleId}", id, detalleId)
                        .header("X-Usuario-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.totalEntradas").value(0));

        assertThat(carritoRepository.findById(id).orElseThrow().getDetalles()).isEmpty();
    }

    @Test
    void vaciarCarrito_valido_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/Carrito/vaciar")
                        .header("X-Carrito-Id", id.toString())
                        .header("X-Usuario-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.estado").value("CANCELADO"));

        var carrito = carritoRepository.findById(id).orElseThrow();
        assertThat(carrito.getEstadoCarrito().name()).isEqualTo("CANCELADO");
        assertThat(carrito.getDetalles()).isEmpty();
    }

    @Test
    void resumenCarrito_conEntradas_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/Carrito/resumen/{id}", id)
                        .header("X-Usuario-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.carritoId").value(id))
                .andExpect(jsonPath("$.data.items").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].eventoId").value(1))
                .andExpect(jsonPath("$.data.totalEntradas").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(30000.0));
    }

    @Test
    void resumenCarrito_sinEntradas_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(get("/api/v1/Carrito/resumen/{id}", id)
                        .header("X-Usuario-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalEntradas").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0));
    }

    @Test
    void listarCarritos_sinCarritos_retorna204() throws Exception {
        mockMvc.perform(get("/api/v1/Carrito/listar")
                        .header("X-Usuario-Id", "999"))
                .andExpect(status().isNoContent());
    }

    @Test
    void crearYAgregarMultiplesEntradas_distintosEventos_retorna200() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isOk());

        var entrada2 = new AgregarEntradaDto(2L, "VIP", 1, new BigDecimal("25000.00"));
        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entrada2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEntradas").value(3))
                .andExpect(jsonPath("$.data.subtotal").value(55000.0));
    }

    @Test
    void actualizarEntrada_existente_modificaCantidad() throws Exception {
        Long id = crearCarritoYRetornarId();

        mockMvc.perform(post("/api/v1/Carrito/{id}/entradas", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaValida)))
                .andExpect(status().isOk());

        var entradaActualizada = new AgregarEntradaDto(1L, "General", 1, new BigDecimal("15000.00"));
        mockMvc.perform(put("/api/v1/Carrito/actualizar/{id}", id)
                        .header("X-Usuario-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entradaActualizada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEntradas").value(1))
                .andExpect(jsonPath("$.data.subtotal").value(15000.0));
    }
}
