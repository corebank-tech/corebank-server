package com.shinhan.corebank.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final String ACCOUNT_NUMBER = "088100000001";
    private static final Long CUSTOMER_ID = 1L;
    private static final String PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    private static final LocalDateTime OPENED_DATE =
            LocalDateTime.of(2026, 8, 10, 10, 0);

    @Test
    @DisplayName("입출금계좌를 생성하면 신규 계좌의 초기 상태로 생성된다")
    void openDemandDepositAccount() {
        // given

        // when
        Account account = Account.open(
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                OPENED_DATE,
                null
        );

        // then
        assertThat(account.getAccountId()).isNull();
        assertThat(account.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(account.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(account.getProductId()).isNull();
        assertThat(account.getAccountType()).isEqualTo(AccountType.DEMAND_DEPOSIT);

        assertThat(account.getBalance()).isZero();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(account.getPasswordFailureCount()).isZero();
        assertThat(account.isPasswordLocked()).isFalse();

        assertThat(account.getAlias()).isNull();
        assertThat(account.getDisplayOrder()).isNull();

        assertThat(account.isWithdrawalRegistered()).isFalse();
        assertThat(account.getWithdrawalRegisteredAt()).isNull();

        assertThat(account.getOpenedDate()).isEqualTo(OPENED_DATE);
        assertThat(account.getMaturityDate()).isNull();
        assertThat(account.getClosedDate()).isNull();
        assertThat(account.getLastTransactionAt()).isNull();

        assertThat(account.getVersion()).isNull();
        assertThat(account.getCreatedAt()).isNull();
        assertThat(account.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("정기예금계좌는 상품 ID와 만기일을 포함하여 생성된다")
    void openTimeDepositAccount() {
        // given
        Long productId = 10L;
        LocalDate maturityDate = LocalDate.of(2027, 8, 10);

        // when
        Account account = Account.open(
                "088200000001",
                CUSTOMER_ID,
                productId,
                AccountType.TIME_DEPOSIT,
                PASSWORD_HASH,
                OPENED_DATE,
                maturityDate
        );

        // then
        assertThat(account.getProductId()).isEqualTo(productId);
        assertThat(account.getAccountType()).isEqualTo(AccountType.TIME_DEPOSIT);
        assertThat(account.getMaturityDate()).isEqualTo(maturityDate);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    @DisplayName("정기적금계좌는 상품 ID와 만기일을 포함하여 생성된다")
    void openInstallmentSavingsAccount() {
        // given
        Long productId = 20L;
        LocalDate maturityDate = LocalDate.of(2027, 8, 10);

        // when
        Account account = Account.open(
                "088300000001",
                CUSTOMER_ID,
                productId,
                AccountType.INSTALLMENT_SAVINGS,
                PASSWORD_HASH,
                OPENED_DATE,
                maturityDate
        );

        // then
        assertThat(account.getProductId()).isEqualTo(productId);
        assertThat(account.getAccountType())
                .isEqualTo(AccountType.INSTALLMENT_SAVINGS);
        assertThat(account.getMaturityDate()).isEqualTo(maturityDate);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    @DisplayName("입출금계좌에 상품 ID가 있으면 생성할 수 없다")
    void rejectDemandDepositWithProductId() {
        // given
        Long productId = 10L;

        // when & then
        assertThatThrownBy(() -> Account.open(
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                productId,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                OPENED_DATE,
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("입출금계좌는 상품 ID를 가질 수 없습니다.");
    }

    @Test
    @DisplayName("입출금계좌에 만기일이 있으면 생성할 수 없다")
    void rejectDemandDepositWithMaturityDate() {
        // given
        LocalDate maturityDate = LocalDate.of(2027, 8, 10);

        // when & then
        assertThatThrownBy(() -> Account.open(
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                OPENED_DATE,
                maturityDate
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("입출금계좌는 만기일을 가질 수 없습니다.");
    }

    @Test
    @DisplayName("정기예금계좌에 상품 ID가 없으면 생성할 수 없다")
    void rejectTimeDepositWithoutProductId() {
        // given
        LocalDate maturityDate = LocalDate.of(2027, 8, 10);

        // when & then
        assertThatThrownBy(() -> Account.open(
                "088200000001",
                CUSTOMER_ID,
                null,
                AccountType.TIME_DEPOSIT,
                PASSWORD_HASH,
                OPENED_DATE,
                maturityDate
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예금·적금 계좌는 유효한 상품 ID가 필요합니다.");
    }

    @Test
    @DisplayName("정기적금계좌에 만기일이 없으면 생성할 수 없다")
    void rejectInstallmentSavingsWithoutMaturityDate() {
        // given
        Long productId = 20L;

        // when & then
        assertThatThrownBy(() -> Account.open(
                "088300000001",
                CUSTOMER_ID,
                productId,
                AccountType.INSTALLMENT_SAVINGS,
                PASSWORD_HASH,
                OPENED_DATE,
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예금·적금 계좌는 만기일이 필요합니다.");
    }
}
