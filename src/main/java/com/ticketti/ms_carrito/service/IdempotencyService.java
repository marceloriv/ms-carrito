package com.ticketti.ms_carrito.service;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.model.IdempotencyRecord;
import com.ticketti.ms_carrito.repository.IdempotencyRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int TTL_HOURS = 24;

    private final IdempotencyRecordRepository idempotencyRepository;

    /**
     * Registra una nueva solicitud idempotente para evitar duplicados.
     *
     * @param idempotencyKey clave enviada por el cliente.
     * @param requestHash huella del request cuando esté disponible.
     * @return registro creado en estado pendiente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord registrarSolicitud(String idempotencyKey, String requestHash) {
        validarClave(idempotencyKey);

        if (idempotencyRepository.existsByKey(idempotencyKey)) {
            throw CarritoException.idempotenciaInvalida();
        }

        IdempotencyRecord registro = new IdempotencyRecord();
        registro.setKey(idempotencyKey);
        registro.setRequestHash(requestHash);
        registro.setStatus(IdempotencyRecord.Status.PENDING);
        registro.setCreatedAt(LocalDateTime.now());
        registro.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
        try {
            return idempotencyRepository.save(registro);
        } catch (DataIntegrityViolationException ex) {
            throw CarritoException.idempotenciaInvalida();
        }
    }

    /**
     * Marca una solicitud idempotente como completada.
     *
     * @param idempotencyKey clave del registro idempotente.
     * @param responseSnapshot respuesta guardada como referencia.
     * @return registro actualizado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord marcarCompletado(String idempotencyKey, String responseSnapshot) {
        IdempotencyRecord registro = obtenerRegistro(idempotencyKey);
        registro.marcarCompletado(responseSnapshot);
        return idempotencyRepository.save(registro);
    }

    /**
     * Marca una solicitud idempotente como fallida.
     *
     * @param idempotencyKey clave del registro idempotente.
     * @return registro actualizado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord marcarFallido(String idempotencyKey) {
        IdempotencyRecord registro = obtenerRegistro(idempotencyKey);
        registro.marcarFallido();
        return idempotencyRepository.save(registro);
    }

    /**
     * Obtiene el registro de idempotencia de forma segura.
     *
     * @param idempotencyKey clave a buscar.
     * @return Optional con el registro si existe.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<IdempotencyRecord> obtenerRecord(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return java.util.Optional.empty();
        }
        return idempotencyRepository.findByKey(idempotencyKey);
    }

    /**
     * Elimina un registro de idempotencia (por ejemplo, para permitir reintentos si falló).
     *
     * @param idempotencyKey clave a eliminar.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void eliminarRecord(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRepository.deleteById(idempotencyKey);
        }
    }

    /**
     * Obtiene el registro idempotente existente o lanza un error de negocio.
     *
     * @param idempotencyKey clave a buscar.
     * @return registro encontrado.
     */
    private IdempotencyRecord obtenerRegistro(String idempotencyKey) {
        validarClave(idempotencyKey);
        return idempotencyRepository.findByKey(idempotencyKey)
                .orElseThrow(CarritoException::idempotenciaInvalida);
    }

    /**
     * Valida que la clave idempotente no sea nula ni vacía.
     *
     * @param idempotencyKey clave a verificar.
     */
    private void validarClave(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw CarritoException.idempotenciaInvalida();
        }
    }
}
