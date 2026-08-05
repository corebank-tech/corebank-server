package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.autotransfer.domain.TransferCycle;
import com.shinhan.corebank.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auto_transfer")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutoTransferJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auto_transfer_id")
    private Long autoTransferId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "withdrawal_account_id", nullable = false)
    private Long withdrawalAccountId;

    @Column(name = "deposit_account_number", nullable = false, columnDefinition = "CHAR(12)")
    private String depositAccountNumber;

    @Column(name = "payee_name", nullable = false, length = 50)
    private String payeeName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "cycle_months", nullable = false, columnDefinition = "TINYINT")
    private Integer cycleMonths;

    @Column(name = "transfer_day", nullable = false, columnDefinition = "TINYINT")
    private Integer transferDay;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;

    @Column(name = "my_passbook_memo", length = 10)
    private String myPassbookMemo;

    @Column(name = "recipient_passbook_memo", length = 10)
    private String recipientPassbookMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AutoTransferStatus status;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "autoTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<AutoTransferExecutionJpaEntity> executions = new ArrayList<>();

    // 등록
    public static AutoTransferJpaEntity register(
            Long customerId, Long withdrawalAccountId, String depositAccountNumber, String payeeName,
            Long amount, Integer cycleMonths, Integer transferDay,
            LocalDate startDate, LocalDate endDate, LocalDate nextExecutionDate,
            String myPassbookMemo, String recipientPassbookMemo, LocalDateTime now) {

        TransferCycle.fromMonths(cycleMonths);

        if (transferDay < 1 || transferDay > 31) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_DAY);
        }
        LocalDate today = now.toLocalDate();
        if (!startDate.isAfter(today)) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 시작일은 당일이나 과거일 수 없습니다.");
        }
        if (startDate.isAfter(today.plusDays(365))) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 시작일은 익일부터 1년 이내여야 합니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일 이후여야 합니다.");
        }
        if (endDate.isAfter(startDate.plusMonths(60))) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일로부터 60개월 이내여야 합니다.");
        }
        if (nextExecutionDate.isAfter(endDate)) {
            throw new BusinessException(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD);
        }

        AutoTransferJpaEntity e = new AutoTransferJpaEntity();
        e.customerId = customerId;
        e.withdrawalAccountId = withdrawalAccountId;
        e.depositAccountNumber = depositAccountNumber;
        e.payeeName = payeeName;
        e.amount = amount;
        e.cycleMonths = cycleMonths;
        e.transferDay = transferDay;
        e.startDate = startDate;
        e.endDate = endDate;
        e.nextExecutionDate = nextExecutionDate;
        e.myPassbookMemo = myPassbookMemo;
        e.recipientPassbookMemo = recipientPassbookMemo;
        e.status = AutoTransferStatus.NORMAL;
        e.registeredAt = now;
        return e;
    }

    // 해지
    public void terminate(LocalDateTime now) {
        if (!this.status.isModifiable()) {
            throw new BusinessException(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS);
        }
        if (this.nextExecutionDate.equals(now.toLocalDate())) {
            throw new BusinessException(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE);
        }
        this.status = AutoTransferStatus.TERMINATED;
        this.terminatedAt = now;
    }

    // 이체 종료
    public void expire(LocalDateTime now) {
        this.status = AutoTransferStatus.EXPIRED;
        this.terminatedAt = now;
    }

    // 다음 실행 예정일. transferDay 기준으로 계산해 월말 보정이 누적 X
    public void advanceNextExecutionDate() {
        YearMonth targetMonth = YearMonth.from(this.nextExecutionDate).plusMonths(this.cycleMonths);
        int day = Math.min(this.transferDay, targetMonth.lengthOfMonth());
        this.nextExecutionDate = targetMonth.atDay(day);
    }

    // 회차 리스트에 새 회차를 추가
    public void addExecution(AutoTransferExecutionJpaEntity execution) {
        this.executions.add(execution);
        execution.assignTo(this);
    }

    // 금액, 주기, 종료일, 통장 표시 내용 변경
    public void change(Long amount, Integer cycleMonths, LocalDate endDate,
                        String myPassbookMemo, String recipientPassbookMemo) {
        if (!this.status.isModifiable()) {
            throw new BusinessException(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS);
        }
        TransferCycle.fromMonths(cycleMonths);
        if (endDate.isBefore(this.startDate)) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일 이후여야 합니다.");
        }
        if (endDate.isAfter(this.startDate.plusMonths(60))) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일로부터 60개월 이내여야 합니다.");
        }

        if (!cycleMonths.equals(this.cycleMonths)) {
            // 직전 실행 예정일 기준 재산출: 현재 nextExecutionDate를 옛 주기만큼 되돌린 지점(앵커)에 새 주기 더하는 방식
            YearMonth anchor = YearMonth.from(this.nextExecutionDate).minusMonths(this.cycleMonths);
            YearMonth targetMonth = anchor.plusMonths(cycleMonths);
            int day = Math.min(this.transferDay, targetMonth.lengthOfMonth());
            this.nextExecutionDate = targetMonth.atDay(day);
            this.cycleMonths = cycleMonths;
        }
        if (this.nextExecutionDate.isAfter(endDate)) {
            throw new BusinessException(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD);
        }

        this.amount = amount;
        this.endDate = endDate;
        this.myPassbookMemo = myPassbookMemo;
        this.recipientPassbookMemo = recipientPassbookMemo;
    }
}
