package com.ticketti.ms_carrito.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketti.ms_carrito.model.IdempotencyRecord;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByKey(String key);

    boolean existsByKey(String key);

    void deleteByExpiresAtBefore(LocalDateTime date);
}