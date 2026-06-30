package com.ticketti.ms_carrito.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.model.NonceRecord;

@Repository
public interface NonceRecordRepository extends JpaRepository<NonceRecord, String> {

    boolean existsByNonce(String nonce);

    void deleteByExpiresAtBefore(LocalDateTime date);
}
