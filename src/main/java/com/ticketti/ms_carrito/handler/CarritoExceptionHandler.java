package com.ticketti.ms_carrito.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticketti.ms_carrito.dto.ApiRespuestaDto;
import com.ticketti.ms_carrito.exception.CarritoException;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler centralizado para todas las excepciones del carrito.
 * Maneja la excepción unificada CarritoException y errores de validación.
 */
@RestControllerAdvice
@Slf4j
public class CarritoExceptionHandler {

    @ExceptionHandler(CarritoException.class)
    public ResponseEntity<ApiRespuestaDto<Void>> handleCarritoException(CarritoException ex) {
        HttpStatus status = switch (ex.getCodigo()) {
            case CARRO_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case DETALLE_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case RESERVA_EXPIRADA, RESERVA_YA_RENOVADA -> HttpStatus.GONE;
            case IDEMPOTENCIA_INVALIDA -> HttpStatus.CONFLICT;
            case PAGO_NO_APROBADO -> HttpStatus.PAYMENT_REQUIRED;
            case DEVOLUCION_NO_PERMITIDA -> HttpStatus.FORBIDDEN;
            case WEBHOOK_INVALIDO, HMAC_INVALIDO, TIMESTAMP_INVALIDO, NONCE_REPETIDO -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
                .body(ApiRespuestaDto.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiRespuestaDto<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiRespuestaDto.error("Error de validación", errors));
    }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiRespuestaDto<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        violations.forEach(violation -> errors.put(
            violation.getPropertyPath().toString(),
            violation.getMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiRespuestaDto.error("Error de validación", errors));
        }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiRespuestaDto<Void>> handleOptimisticLockException(Exception ex) {
        log.warn("Conflicto de concurrencia detectado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiRespuestaDto.error("El recurso fue modificado por otro usuario. Por favor, intente nuevamente."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiRespuestaDto<Void>> handleGenericException(Exception ex) {
        log.error("Error interno no esperado: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiRespuestaDto.error("Error interno del servidor. Por favor, contacte al soporte."));
    }
}
