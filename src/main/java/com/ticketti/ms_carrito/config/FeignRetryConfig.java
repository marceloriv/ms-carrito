package com.ticketti.ms_carrito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Retryer;

@Configuration
public class FeignRetryConfig {

    /**
     * Crea el reintentador de Feign para tolerar fallos transitorios en llamadas HTTP.
     *
     * @return configuración de reintentos con backoff lineal.
     */
    @Bean
    public Retryer reintentadorFeign() {
        return new Retryer.Default(1000, 2000, 3);
    }
}
