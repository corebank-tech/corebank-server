package com.shinhan.corebank.account.domain;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

//이후 각 api에서 필요한 함수는 나중에 구현
@Getter
public class Account {

    private final Long accountId;

    private final String accountNumber;
    private final Long customerId;
    private final Long productId;
    private final AccountType accountType;

    private long balance;
    private AccountStatus status;

    private final String passwordHash;
    private int passwordFailureCount;
    private boolean passwordLocked;

    private String alias;
    private Integer displayOrder;

    private boolean withdrawalRegistered;
    private LocalDateTime withdrawalRegisteredAt;

    private final LocalDateTime openedDate;
    private final LocalDate maturityDate;
    private LocalDateTime closedDate;
    private LocalDateTime lastTransactionAt;

    private final Long version;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private static final int MAX_ALIAS_LENGTH = 24;
    private static final int MAX_KOREAN_ALIAS_LENGTH = 12;

    private Account(
            Long accountId,
            String accountNumber,
            Long customerId,
            Long productId,
            AccountType accountType,
            long balance,
            AccountStatus status,
            String passwordHash,
            int passwordFailureCount,
            boolean passwordLocked,
            String alias,
            Integer displayOrder,
            boolean withdrawalRegistered,
            LocalDateTime withdrawalRegisteredAt,
            LocalDateTime openedDate,
            LocalDate maturityDate,
            LocalDateTime closedDate,
            LocalDateTime lastTransactionAt,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.productId = productId;
        this.accountType = accountType;
        this.balance = balance;
        this.status = status;
        this.passwordHash = passwordHash;
        this.passwordFailureCount = passwordFailureCount;
        this.passwordLocked = passwordLocked;
        this.alias = alias;
        this.displayOrder = displayOrder;
        this.withdrawalRegistered = withdrawalRegistered;
        this.withdrawalRegisteredAt = withdrawalRegisteredAt;
        this.openedDate = openedDate;
        this.maturityDate = maturityDate;
        this.closedDate = closedDate;
        this.lastTransactionAt = lastTransactionAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        validate();
    }

    public static Account open(
            String accountNumber,
            Long customerId,
            Long productId,
            AccountType accountType,
            String passwordHash,
            LocalDateTime openedDate,
            LocalDate maturityDate
    ) {
        return new Account(
                null,
                accountNumber,
                customerId,
                productId,
                accountType,
                0L,
                AccountStatus.ACTIVE,
                passwordHash,
                0,
                false,
                null,
                null,
                false,
                null,
                openedDate,
                maturityDate,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static Account reconstitute(
            Long accountId,
            String accountNumber,
            Long customerId,
            Long productId,
            AccountType accountType,
            long balance,
            AccountStatus status,
            String passwordHash,
            int passwordFailureCount,
            boolean passwordLocked,
            String alias,
            Integer displayOrder,
            boolean withdrawalRegistered,
            LocalDateTime withdrawalRegisteredAt,
            LocalDateTime openedDate,
            LocalDate maturityDate,
            LocalDateTime closedDate,
            LocalDateTime lastTransactionAt,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new Account(
                accountId,
                accountNumber,
                customerId,
                productId,
                accountType,
                balance,
                status,
                passwordHash,
                passwordFailureCount,
                passwordLocked,
                alias,
                displayOrder,
                withdrawalRegistered,
                withdrawalRegisteredAt,
                openedDate,
                maturityDate,
                closedDate,
                lastTransactionAt,
                version,
                createdAt,
                updatedAt
        );
    }

    public void changeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }

        String normalizedAlias = alias.strip();

        validateAlias(normalizedAlias);

        this.alias = normalizedAlias;
    }

    public void removeAlias() {
        this.alias = null;
    }

    public void validateWithdrawalRegistrationAllowed() {
        if (accountType != AccountType.DEMAND_DEPOSIT) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_WITHDRAWAL_ACCOUNT_TYPE
            );
        }

        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_ACCOUNT_STATUS
            );
        }
    }

    public void registerWithdrawalAccount(
            LocalDateTime registeredAt
    ) {
        if (withdrawalRegistered) {
            return;
        }

        validateWithdrawalRegistrationAllowed();

        if (registeredAt == null) {
            throw new IllegalArgumentException(
                    "출금계좌 등록 시각은 필수입니다."
            );
        }

        this.withdrawalRegistered = true;
        this.withdrawalRegisteredAt = registeredAt;
    }

    public void unregisterWithdrawalAccount() {
        if (!withdrawalRegistered) {
            return;
        }

        this.withdrawalRegistered = false;
        this.withdrawalRegisteredAt = null;
    }

    public void changeDisplayOrder(int displayOrder) {
        if (displayOrder <= 0) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_DISPLAY_ORDER
            );
        }
        this.displayOrder = displayOrder;
    }

    public void resetDisplayOrder() {
        this.displayOrder = null;
    }

    private void validateAlias(String alias) {
        int totalLength =
                alias.codePointCount(0, alias.length());

        long koreanLength =
                alias.codePoints()
                        .filter(this::isKoreanSyllable)
                        .count();

        if (totalLength > MAX_ALIAS_LENGTH
                || koreanLength > MAX_KOREAN_ALIAS_LENGTH) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_ACCOUNT_ALIAS
            );
        }
    }

    private boolean isKoreanSyllable(int codePoint) {
        return codePoint >= 0xAC00
                && codePoint <= 0xD7A3;
    }

    private void validate() {
        validateAccountNumber();
        validateCustomer();
        validateBalance();
        validatePasswordHash();
        validateOpenedDate();
        validateProductAndMaturity();
        validatePasswordLockState();
        validateWithdrawalRegistration();
        validateClosedState();
        validateDisplayOrder();
    }

    private void validateAccountNumber() {
        if (accountNumber == null || !accountNumber.matches("^[0-9]{12}$")) {
            throw new IllegalStateException("계좌번호는 숫자 12자리여야 합니다.");
        }
    }

    private void validateCustomer() {
        if (customerId == null || customerId <= 0) {
            throw new IllegalStateException("유효한 고객 ID가 필요합니다.");
        }
    }

    private void validateBalance() {
        if (balance < 0) {
            throw new IllegalStateException("계좌 잔액은 음수일 수 없습니다.");
        }
    }

    //암호화 서비스 확정 후 수정
    private void validatePasswordHash() {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalStateException("계좌 비밀번호 해시는 필수입니다.");
        }
    }

    private void validateOpenedDate() {
        if (openedDate == null) {
            throw new IllegalStateException("계좌 개설 시각은 필수입니다.");
        }
    }

    private void validateProductAndMaturity() {
        if (accountType == null) {
            throw new IllegalStateException("계좌 유형은 필수입니다.");
        }

        if (accountType == AccountType.DEMAND_DEPOSIT) {
            if (productId != null) {
                throw new IllegalStateException("입출금계좌는 상품 ID를 가질 수 없습니다.");
            }

            if (maturityDate != null) {
                throw new IllegalStateException("입출금계좌는 만기일을 가질 수 없습니다.");
            }

            return;
        }

        if (productId == null || productId <= 0) {
            throw new IllegalStateException("예금·적금 계좌는 유효한 상품 ID가 필요합니다.");
        }

        if (maturityDate == null) {
            throw new IllegalStateException("예금·적금 계좌는 만기일이 필요합니다.");
        }

        if (maturityDate.atStartOfDay().isBefore(openedDate)) {
            throw new IllegalStateException(
                    "만기일은 개설일보다 이전일 수 없습니다."
            );
        }
    }

    private void validatePasswordLockState() {
        if (passwordFailureCount < 0 || passwordFailureCount > 5) {
            throw new IllegalStateException("비밀번호 실패 횟수가 유효하지 않습니다.");
        }

        boolean shouldBeLocked = passwordFailureCount == 5;

        if (passwordLocked != shouldBeLocked) {
            throw new IllegalStateException("비밀번호 실패 횟수와 잠금 상태가 일치하지 않습니다.");
        }
    }

    private void validateWithdrawalRegistration() {
        if (withdrawalRegistered
                && accountType != AccountType.DEMAND_DEPOSIT) {
            throw new IllegalStateException("출금계좌 등록은 입출금계좌만 가능합니다.");
        }

        if (withdrawalRegistered && withdrawalRegisteredAt == null) {
            throw new IllegalStateException("출금계좌 등록 시각이 필요합니다.");
        }

        if (!withdrawalRegistered && withdrawalRegisteredAt != null) {
            throw new IllegalStateException("미등록 계좌는 출금계좌 등록 시각을 가질 수 없습니다.");
        }
    }

    private void validateClosedState() {
        if (status == null) {
            throw new IllegalStateException("계좌 상태는 필수입니다.");
        }

        if (status == AccountStatus.CLOSED && closedDate == null) {
            throw new IllegalStateException("해지 계좌는 해지 시각이 필요합니다.");
        }

        if (status != AccountStatus.CLOSED && closedDate != null) {
            throw new IllegalStateException("해지되지 않은 계좌는 해지 시각을 가질 수 없습니다.");
        }

        if (closedDate != null && closedDate.isBefore(openedDate)) {
            throw new IllegalStateException(
                    "해지 시각은 개설 시각보다 이전일 수 없습니다."
            );
        }
    }

    private void validateDisplayOrder() {
        if (displayOrder != null && displayOrder <= 0) {
            throw new IllegalStateException(
                    "계좌 표시순서는 1 이상이어야 합니다."
            );
        }
    }
}

