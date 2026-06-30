package com.ticketti.ms_carrito.service;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketti.ms_carrito.exception.CarritoException;
import com.ticketti.ms_carrito.model.NonceRecord;
import com.ticketti.ms_carrito.repository.NonceRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NonceService {

    private static final int TTL_MINUTES = 60;

    private final NonceRecordRepository nonceRepository;

    /**
     * Valida que el nonce no haya sido utilizado anteriormente.
     * Si es nuevo, lo persiste para prevenir replays futuros.
     *
     * @param nonce valor del nonce del webhook.
     * @param carritoId ID del carrito asociado (para trazabilidad).
     */
    @Transactional
    public void validarNonce(String nonce, Long carritoId) {
        if (nonce == null || nonce.isBlank()) {
            return;
        }

        if (nonceRepository.existsByNonce(nonce)) {
            throw CarritoException.nonceRepetido();
        }

        NonceRecord record = new NonceRecord();
        record.setNonce(nonce);
        record.setUsedAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        record.setCarritoId(carritoId);

        try {
            nonceRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            throw CarritoException.nonceRepetido();
        }
    }

    /**
     * Limpia nonces expirados periódicamente (cada hora).
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void limpiarNoncesExpirados() {
        nonceRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.debug("Limpieza de nonces expirados completada");
    }
}
