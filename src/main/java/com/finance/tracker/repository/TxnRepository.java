package com.finance.tracker.repository;

import com.finance.tracker.domain.Txn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TxnRepository extends JpaRepository<Txn, UUID>, JpaSpecificationExecutor<Txn> {
    Optional<Txn> findByUserIdAndId(UUID userId, UUID id);

    void deleteByUserIdAndId(UUID userId, UUID id);

    List<Txn> findByUserIdAndExternalIdIn(UUID userId, Collection<String> externalIds);

    @Query("select t.externalId from Txn t where t.userId = :userId and t.externalId in :ids")
    Set<String> findExternalIdsByUserId(@Param("userId") UUID userId, @Param("ids") Collection<String> ids);
}
