package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "auto_transfer_execution",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_ate_dup",
                        columnNames = {"auto_transfer_id", "execution_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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
    private String transactionNumber; // 성공 또는 채번 이후 실패시에만. 채번 이전 실패는 null

    @Column(name = "failure_reason", length = 200)
    private String failureReason; // 실패 시에만. 성공은 null

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt; // 실제 실행 시각, 예정일과 다를 수 있음
}
