package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledTransferJpaRepository extends JpaRepository<ScheduledTransferJpaEntity, Long> {
    boolean existsByCustomerIdAndWithdrawalAccountIdAndPayeeAccountNumberAndAmountAndScheduledDateAndStatus(
            Long customerId,
            Long withdrawalAccountId,
            String payeeAccountNumber,
            Long amount,
            LocalDate scheduledDate,
            ScheduledTransferStatus status);

    List<ScheduledTransferJpaEntity> findByStatusAndScheduledDateOrderByRegisteredAtAsc(
            ScheduledTransferStatus status, LocalDate scheduledDate);

    // PROCESSING에 멈춘 건 전체 조회 (재확정 배치 대상)
    List<ScheduledTransferJpaEntity> findByStatus(ScheduledTransferStatus status);

    // WAITING -> PROCESSING 조건부 UPDATE. 영향받은 행이 1이면 선점 성공, 0이면 이미 다른 실행이 선점함.
    // version을 함께 올려, 이 건을 WAITING으로 읽어둔 취소 요청의 저장이 낙관적 락에 걸리도록 한다(PR #335 리뷰 R2).
    @Modifying
    @Query(
            value = "UPDATE scheduled_transfer SET status = 'PROCESSING', version = version + 1 "
                    + "WHERE scheduled_transfer_id = :id AND status = 'WAITING'",
            nativeQuery = true)
    int claimForProcessing(@Param("id") Long id);

    // PROCESSING -> 최종 상태(SUCCESS/FAILED) 조건부 UPDATE. 영향받은 행이 1이면 확정 성공,
    // 0이면 이미 다른 재확정 실행이 먼저 이 건을 확정한 것(중복 재확정 방어).
    @Modifying
    @Query(
            value = "UPDATE scheduled_transfer SET status = :status, transaction_number = :transactionNumber, "
                    + "failure_reason = :failureReason, executed_at = :executedAt "
                    + "WHERE scheduled_transfer_id = :id AND status = 'PROCESSING'",
            nativeQuery = true)
    int finalizeIfProcessing(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("transactionNumber") String transactionNumber,
            @Param("failureReason") String failureReason,
            @Param("executedAt") LocalDateTime executedAt);
}
