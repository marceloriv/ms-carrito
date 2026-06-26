package com.ticketti.ms_carrito.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Configuracion de seguridad Spring Security.
 * JWT-only, sin sesiones, stateless.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthFilter;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/swagger-ui/index.html").permitAll()
						.requestMatchers("/v3/api-docs/**", "/v3/api-docs", "/api-docs/**").permitAll()
						.requestMatchers("/webjars/**", "/swagger-resources/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/info", "/actuator").permitAll()

						// === Rutas que requieren autenticación ===
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/webhooks/**").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/checkout/**").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/renovar/**").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/pago-manual/**").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/devoluciones/**").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/Carrito/listar").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/estadisticas").authenticated()

						// === Rutas públicas (guest puede operar con cartId) ===
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/crear").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/Carrito/obtener/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/Carrito/resumen/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/*/entradas").permitAll()
						.requestMatchers(HttpMethod.DELETE, "/api/v1/Carrito/*/entradas/**").permitAll()
						.requestMatchers(HttpMethod.PUT, "/api/v1/Carrito/actualizar/**").permitAll()
						.requestMatchers(HttpMethod.DELETE, "/api/v1/Carrito/vaciar").permitAll()

						// Catch-all: cualquier otra ruta de carrito requiere auth
						.requestMatchers("/api/v1/Carrito/**").authenticated()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
