package com.finance.tracker.repository;

import com.finance.tracker.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshTokenHashAndRevokedAtIsNull(String hash);

    @Modifying
    @Query("update Session s set s.revokedAt = :now where s.refreshTokenHash = :hash and s.revokedAt is null")
    int revokeByHash(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("update Session s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
