package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "auto_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EntityListeners(AuditingEntityListener.class)
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 기존 : excutions 클래스 레벨 @Getter로 노출 및 orphanRemoval=true+cascade=ALL -> 부모를 지우면 이미 저장된 회차 DB 삭제될 수 있음
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "autoTransfer", cascade = CascadeType.PERSIST)
    private final List<AutoTransferExecutionJpaEntity> executions = new ArrayList<>();

    public List<AutoTransferExecutionJpaEntity> getExecutions() {
        return Collections.unmodifiableList(executions);
    }
}
