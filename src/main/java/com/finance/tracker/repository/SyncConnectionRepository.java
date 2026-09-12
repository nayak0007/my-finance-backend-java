package com.finance.tracker.repository;

import com.finance.tracker.domain.SyncConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncConnectionRepository extends JpaRepository<SyncConnection, UUID> {
    Optional<SyncConnection> findByUserIdAndProvider(UUID userId, String provider);

    List<SyncConnection> findByUserIdOrderByProvider(UUID userId);

    Optional<SyncConnection> findByProviderAndState(String provider, String state);
}