package com.finance.tracker.repository;

import com.finance.tracker.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);

    Optional<Account> findByUserIdAndId(UUID userId, UUID id);

    void deleteByUserIdAndId(UUID userId, UUID id);

    @Query("select count(t) from Txn t where t.userId = :userId and t.accountId = :accountId")
    long countTransactions(@Param("userId") UUID userId, @Param("accountId") UUID accountId);
}
