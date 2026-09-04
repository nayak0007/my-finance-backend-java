package com.finance.tracker.repository;

import com.finance.tracker.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    List<Holding> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
