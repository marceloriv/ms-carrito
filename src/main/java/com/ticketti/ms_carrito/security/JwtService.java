package com.ticketti.ms_carrito.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para validacion y extraccion de claims JWT.
 * Algoritmo: HS256.
 */
@Service
@Slf4j
public class JwtService {

	@Value("${jwt.secret:test-secret-key-ms-carrito-2026-very-secure}")
	private String secretKey;

	@Value("${jwt.issuer:ms-carrito}")
	private String issuer;

	@Value("${jwt.audience:ms-carrito}")
	private String audience;

	private SecretKey getSigningKey() {
		return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
	}

	public Long getUserIdFromContext() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new SecurityException("Usuario no autenticado");
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof String userIdStr) {
			return Long.parseLong(userIdStr);
		}
		throw new SecurityException("Formato de usuario invalido");
	}

	public String getRoleFromContext() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new SecurityException("Usuario no autenticado");
		}
		return authentication.getAuthorities().stream()
				.findFirst()
				.map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
				.orElseThrow(() -> new SecurityException("Rol no encontrado"));
	}

	public Long extractUserId(String token) {
		return Long.parseLong(extractClaim(token, Claims::getSubject));
	}

	public String extractRole(String token) {
		return extractClaim(token, claims -> claims.get("role", String.class));
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public String extractIssuer(String token) {
		return extractClaim(token, Claims::getIssuer);
	}

	public String extractAudience(String token) {
		return extractClaim(token, claims -> claims.getAudience().iterator().next());
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSigningKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public boolean isTokenValid(String token) {
		try {
			final String extractedIssuer = extractIssuer(token);
			final String extractedAudience = extractAudience(token);

			return extractedIssuer.equals(issuer)
					&& extractedAudience.equals(audience)
					&& !isTokenExpired(token);
		} catch (Exception e) {
			log.error("Error validando token: {}", e.getMessage());
			return false;
		}
	}

	private boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	public boolean validateTokenClaims(String token) {
		try {
			Claims claims = extractAllClaims(token);
			return claims.getSubject() != null
					&& claims.get("role") != null
					&& claims.getExpiration() != null
					&& claims.getIssuer() != null
					&& claims.getAudience() != null;
		} catch (Exception e) {
			log.error("Token invalido: {}", e.getMessage());
			return false;
		}
	}
}