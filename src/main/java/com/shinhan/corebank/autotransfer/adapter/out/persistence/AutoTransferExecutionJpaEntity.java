package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "auto_transfer_execution",
        uniqueConstraints = @UniqueConstraint(name = "uk_ate_dup", columnNames = {"auto_transfer_id", "execution_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoTransferExecutionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auto_transfer_id", nullable = false)
    private AutoTransferJpaEntity autoTransfer;

    @Column(name = "execution_date", nullable = false)
    private LocalDate executionDate;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ProcessResultStatus status;

    @Column(name = "transaction_number", columnDefinition = "CHAR(20)")
    private String transactionNumber;   // 성공 시에만. 실패는 null

    @Column(name = "failure_reason", length = 200)
    private String failureReason;   // 실패 시에만. 성공은 null

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;   // 실제 실행 시각, 예정일과 다를 수 있음

    // 성공
    public static AutoTransferExecutionJpaEntity success(
            LocalDate executionDate, Long amount, String transactionNumber, LocalDateTime executedAt) {

        AutoTransferExecutionJpaEntity e = new AutoTransferExecutionJpaEntity();
        e.executionDate = executionDate;
        e.amount = amount;
        e.status = ProcessResultStatus.SUCCESS;
        e.transactionNumber = transactionNumber;
        e.executedAt = executedAt;
        return e;
    }

    // 에러
    public static AutoTransferExecutionJpaEntity error(
            LocalDate executionDate, Long amount, String failureReason, LocalDateTime executedAt) {

        AutoTransferExecutionJpaEntity e = new AutoTransferExecutionJpaEntity();
        e.executionDate = executionDate;
        e.amount = amount;
        e.status = ProcessResultStatus.ERROR;
        e.failureReason = failureReason;
        e.executedAt = executedAt;
        return e;
    }


    /** 부모가 addExecution 할 때 호출한다 */
    void assignTo(AutoTransferJpaEntity parent) {
        this.autoTransfer = parent;
    }
}
