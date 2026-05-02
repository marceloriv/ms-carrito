package com.ticketti.ms_carrito;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticketti.ms_carrito.client.EventoClient;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class MsCarritoApplicationTests {

	@MockitoBean
	private EventoClient eventoClient;

	@Test
	void contextLoads() {
	}

}
