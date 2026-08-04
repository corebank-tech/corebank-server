package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auto_transfer")
@Getter
@RequiredArgsConstructor
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

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "autoTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<AutoTransferExecutionJpaEntity> executions = new ArrayList<>();

    // 등록
    public static AutoTransferJpaEntity register(
            Long customerId, Long withdrawalAccountId, String depositAccountNumber, String payeeName,
            Long amount, Integer cycleMonths, Integer transferDay,
            LocalDate startDate, LocalDate endDate, LocalDate nextExecutionDate,
            String myPassbookMemo, String recipientPassbookMemo, LocalDateTime now) {

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
            throw new IllegalStateException("정상 상태가 아닌 자동이체입니다.");
        }
        this.status = AutoTransferStatus.TERMINATED;
        this.terminatedAt = now;
    }

    // 이체 종료
    public void expire(LocalDateTime now) {
        this.status = AutoTransferStatus.EXPIRED;
        this.terminatedAt = now;
    }

    // 다음 실행 예정일
    public void advanceNextExecutionDate() {
        this.nextExecutionDate = this.nextExecutionDate.plusMonths(this.cycleMonths);
    }

    // 회차 리스트에 새 회차를 추가
    public void addExecution(AutoTransferExecutionJpaEntity execution) {
        this.executions.add(execution);
        execution.assignTo(this);
    }
}
