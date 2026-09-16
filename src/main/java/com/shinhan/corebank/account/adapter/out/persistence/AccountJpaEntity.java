package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "account_number", nullable = false, unique = true, columnDefinition = "CHAR(12)")
    private String accountNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    private AccountType accountType;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AccountStatus status;

    @Column(name = "password_hash", nullable = false, columnDefinition = "CHAR(60)")
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

    @Column(name = "withdrawal_registered_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime withdrawalRegisteredAt;

    @Column(name = "opened_date", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime openedDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "closed_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime closedDate;

    @Column(name = "last_transaction_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastTransactionAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void updateFrom(Account account) {
        this.balance = account.getBalance();
        this.status = account.getStatus();
        this.passwordHash = account.getPasswordHash();
        this.passwordFailureCount = (byte) account.getPasswordFailureCount();
        this.passwordLocked = account.isPasswordLocked();
        this.alias = account.getAlias();
        this.displayOrder = account.getDisplayOrder();
        this.withdrawalRegistered = account.isWithdrawalRegistered();
        this.withdrawalRegisteredAt = account.getWithdrawalRegisteredAt();
        this.closedDate = account.getClosedDate();
        this.lastTransactionAt = account.getLastTransactionAt();
    }

    // 계좌비밀번호 해시와 실패 횟수 및 잠금 상태를 반영한다.
    public void updatePasswordState(Account account) {
        this.passwordHash = account.getPasswordHash();
        this.passwordFailureCount = (byte) account.getPasswordFailureCount();
        this.passwordLocked = account.isPasswordLocked();
    }
}
