package com.finance.tracker.repository;

import com.finance.tracker.domain.Txn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TxnRepository extends JpaRepository<Txn, UUID>, JpaSpecificationExecutor<Txn> {
    Optional<Txn> findByUserIdAndId(UUID userId, UUID id);

    void deleteByUserIdAndId(UUID userId, UUID id);
}
