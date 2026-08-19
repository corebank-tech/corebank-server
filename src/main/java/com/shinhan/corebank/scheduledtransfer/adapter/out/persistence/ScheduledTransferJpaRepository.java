package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduledTransferJpaRepository extends JpaRepository<ScheduledTransferJpaEntity, Long> {
    boolean existsByCustomerIdAndWithdrawalAccountIdAndPayeeAccountNumberAndAmountAndScheduledDateAndStatus(
            Long customerId, Long withdrawalAccountId, String payeeAccountNumber, Long amount,
            LocalDate scheduledDate, ScheduledTransferStatus status);

    List<ScheduledTransferJpaEntity> findByStatusAndScheduledDateOrderByRegisteredAtAsc(
            ScheduledTransferStatus status, LocalDate scheduledDate);

    // PROCESSING에 멈춘 건 전체 조회 (재확정 배치 대상)
    List<ScheduledTransferJpaEntity> findByStatus(ScheduledTransferStatus status);

    // WAITING -> PROCESSING 조건부 UPDATE. 영향받은 행이 1이면 선점 성공, 0이면 이미 다른 실행이 선점함.
    @Modifying
    @Query(value = "UPDATE scheduled_transfer SET status = 'PROCESSING' WHERE scheduled_transfer_id = :id AND status = 'WAITING'",
            nativeQuery = true)
    int claimForProcessing(@Param("id") Long id);
}
