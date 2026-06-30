package com.ticketti.ms_carrito;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.ticketti.ms_carrito.client.EventoClient;
import com.ticketti.ms_carrito.repository.CarritoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class MsCarritoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CarritoRepository carritoRepository;

    @MockitoBean
    private EventoClient eventoClient;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void crearCarrito_DebePersistirEnBaseDeDatosYRetornarCreado() throws Exception {
        mockMvc.perform(post("/api/v1/Carrito/crear")
                .header("X-Usuario-Id", "1")
                .header("X-Rol-Usuario-Id", "CLIENTE")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.data.idCarrito").exists());

        // Verificar que hay al menos un carrito guardado en la base de datos
        long count = carritoRepository.count();
        assertThat(count).isGreaterThan(0);
    }
}
