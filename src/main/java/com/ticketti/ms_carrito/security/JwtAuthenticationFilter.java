package com.ticketti.ms_carrito.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro de autenticación JWT.
 * Extrae token del header Authorization y valida claims.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	private static final List<String> PUBLIC_PATHS = List.of(
			"/api/v1/webhooks",
			"/actuator/health",
			"/actuator/info",
			"/swagger-ui",
			"/v3/api-docs"
	);

	@Override
	protected void doFilterInternal(
			@Nonnull HttpServletRequest request,
			@Nonnull HttpServletResponse response,
			@Nonnull FilterChain filterChain
	) throws ServletException, IOException {

		final String authHeader = request.getHeader("Authorization");
		final String jwt;
		final String userId;

		if (isPublicPath(request.getRequestURI())) {
			filterChain.doFilter(request, response);
			return;
		}

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			log.warn("Header Authorization no valido o ausente");
			filterChain.doFilter(request, response);
			return;
		}

		jwt = authHeader.substring(7);

		try {
			if (!jwtService.isTokenValid(jwt) || !jwtService.validateTokenClaims(jwt)) {
				log.error("Token JWT invalido o claims incompletos");
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getWriter().write("Token JWT invalido");
				return;
			}

			userId = String.valueOf(jwtService.extractUserId(jwt));
			String role = jwtService.extractRole(jwt);

			if (SecurityContextHolder.getContext().getAuthentication() == null) {
				UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
						userId,
						null,
						Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
				);
				authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authToken);
				log.debug("Autenticacion exitosa para usuario: {}, rol: {}", userId, role);
			}
		} catch (JwtException | IllegalArgumentException e) {
			log.error("Error procesando JWT: {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().write("Error de autenticacion");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean isPublicPath(String uri) {
		return PUBLIC_PATHS.stream().anyMatch(uri::startsWith);
	}
}
