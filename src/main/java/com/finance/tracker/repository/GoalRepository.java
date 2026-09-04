package com.finance.tracker.repository;

import com.finance.tracker.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserId(UUID userId);

    Optional<Goal> findByUserIdAndId(UUID userId, UUID id);

    void deleteByUserId(UUID userId);
}
