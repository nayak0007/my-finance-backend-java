package com.finance.tracker.repository;

import com.finance.tracker.domain.InvestmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvestmentHistoryRepository extends JpaRepository<InvestmentHistory, UUID> {
    List<InvestmentHistory> findByUserIdOrderByDateDesc(UUID userId);

    void deleteByUserId(UUID userId);
}
