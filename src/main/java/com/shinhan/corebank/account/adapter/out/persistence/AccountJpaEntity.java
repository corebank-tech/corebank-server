package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AccountJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(
            name = "account_number",
            nullable = false,
            unique = true,
            columnDefinition = "CHAR(12)"
    )
    private String accountNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    private AccountType accountType;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AccountStatus status;

    @Column(
            name = "password_hash",
            nullable = false,
            columnDefinition = "CHAR(60)"
    )
    private String passwordHash;

    @Column(name = "password_failure_count", nullable = false)
    private byte passwordFailureCount;

    @Column(name = "password_locked", nullable = false)
    private boolean passwordLocked;

    @Column(name = "alias", length = 24)
    private String alias;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "withdrawal_registered", nullable = false)
    private boolean withdrawalRegistered;

    @Column(
            name = "withdrawal_registered_at",
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime withdrawalRegisteredAt;

    @Column(
            name = "opened_date",
            nullable = false,
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime openedDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(
            name = "closed_date",
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime closedDate;

    @Column(
            name = "last_transaction_at",
            columnDefinition = "DATETIME(6)"
    )
    private LocalDateTime lastTransactionAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}