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
						.requestMatchers("/api/v1/webhooks/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/Carrito/crear").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/carrito/crear").permitAll()
						.requestMatchers("/api/v1/Carrito/**").authenticated()
						.requestMatchers("/api/v1/checkout/**").authenticated()
						.requestMatchers("/api/v1/devoluciones/**").authenticated()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
