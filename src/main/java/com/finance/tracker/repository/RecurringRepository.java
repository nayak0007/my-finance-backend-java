package com.finance.tracker.repository;

import com.finance.tracker.domain.Recurring;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringRepository extends JpaRepository<Recurring, UUID> {
    List<Recurring> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
