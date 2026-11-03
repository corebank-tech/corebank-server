package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DelayedTransferSettingJpaRepository extends JpaRepository<DelayedTransferSettingJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DelayedTransferSettingJpaEntity s WHERE s.customerId = :customerId")
    Optional<DelayedTransferSettingJpaEntity> findForUpdate(@Param("customerId") Long customerId);
}
