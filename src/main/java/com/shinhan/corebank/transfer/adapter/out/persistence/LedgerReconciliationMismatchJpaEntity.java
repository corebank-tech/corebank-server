package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ledger_reconciliation_mismatch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LedgerReconciliationMismatchJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_reconciliation_mismatch_id")
    private Long ledgerReconciliationMismatchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "ledger_balance", nullable = false)
    private Long ledgerBalance;

    @Column(name = "account_balance", nullable = false)
    private Long accountBalance;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;
}
