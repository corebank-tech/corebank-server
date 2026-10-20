package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "previous_status", nullable = false, length = 12)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 12)
    private String newStatus;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "effective_business_date")
    private LocalDate effectiveBusinessDate;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    public static AccountStatusHistoryJpaEntity matured(
            Long accountId, LocalDate maturityDate, LocalDate maturityBaseDate, LocalDateTime changedAt) {

        AccountStatusHistoryJpaEntity entity = new AccountStatusHistoryJpaEntity();

        entity.accountId = accountId;
        entity.previousStatus = AccountStatus.ACTIVE.name();
        entity.newStatus = AccountStatus.MATURED.name();
        entity.maturityDate = maturityDate;
        entity.effectiveBusinessDate = maturityBaseDate;
        entity.changedAt = changedAt;
        entity.triggerType = "COB";

        return entity;
    }
}
