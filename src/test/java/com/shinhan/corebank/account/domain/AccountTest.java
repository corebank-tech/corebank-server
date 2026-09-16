package com.shinhan.corebank.account.domain;

import static org.assertj.core.api.Assertions.*;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final String ACCOUNT_NUMBER = "088100000001";
    private static final Long CUSTOMER_ID = 1L;
    private static final String PASSWORD_HASH = "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    private static final LocalDateTime OPENED_DATE = LocalDateTime.of(2026, 8, 10, 10, 0);

    private static final Long ACCOUNT_ID = 1L;
    private static final Long VERSION = 0L;

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 10, 10, 0);

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 10, 11, 0);

    @Test
    @DisplayName("입출금계좌를 생성하면 신규 계좌의 초기 상태로 생성된다")
    void openDemandDepositAccount() {
        // given

        // when
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

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
                maturityDate);

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
                maturityDate);

        // then
        assertThat(account.getProductId()).isEqualTo(productId);
        assertThat(account.getAccountType()).isEqualTo(AccountType.INSTALLMENT_SAVINGS);
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
                        null))
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
                        maturityDate))
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
                        maturityDate))
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
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예금·적금 계좌는 만기일이 필요합니다.");
    }

    @Test
    @DisplayName("저장된 입출금계좌 상태를 정상적으로 복원한다")
    void reconstituteDemandDepositAccount() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        // when
        Account account = Account.reconstitute(
                ACCOUNT_ID,
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                10_000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                0,
                false,
                "생활비",
                1,
                false,
                null,
                openedDate,
                null,
                null,
                openedDate,
                VERSION,
                CREATED_AT,
                UPDATED_AT);

        // then
        assertThat(account.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(account.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(account.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(account.getBalance()).isEqualTo(10_000L);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getAlias()).isEqualTo("생활비");
        assertThat(account.getDisplayOrder()).isEqualTo(1);
        assertThat(account.getVersion()).isEqualTo(VERSION);
        assertThat(account.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(account.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    @DisplayName("해지 시각이 있는 해지 계좌를 정상적으로 복원한다")
    void reconstituteClosedAccount() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 1, 10, 0);

        LocalDateTime closedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        // when
        Account account = Account.reconstitute(
                ACCOUNT_ID,
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                0L,
                AccountStatus.CLOSED,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                false,
                null,
                openedDate,
                null,
                closedDate,
                null,
                VERSION,
                CREATED_AT,
                UPDATED_AT);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);

        assertThat(account.getClosedDate()).isEqualTo(closedDate);
    }

    @Test
    @DisplayName("해지 계좌에 해지 시각이 없으면 복원할 수 없다")
    void failToReconstituteClosedAccountWithoutClosedDate() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 1, 10, 0);

        // when & then
        assertThatThrownBy(() -> Account.reconstitute(
                        ACCOUNT_ID,
                        ACCOUNT_NUMBER,
                        CUSTOMER_ID,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        0L,
                        AccountStatus.CLOSED,
                        PASSWORD_HASH,
                        0,
                        false,
                        null,
                        null,
                        false,
                        null,
                        openedDate,
                        null,
                        null,
                        null,
                        VERSION,
                        CREATED_AT,
                        UPDATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("해지 계좌는 해지 시각이 필요합니다.");
    }

    @Test
    @DisplayName("해지 시각이 개설 시각보다 이전이면 복원할 수 없다")
    void failToReconstituteAccountWhenClosedDateIsBeforeOpenedDate() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        LocalDateTime closedDate = LocalDateTime.of(2026, 8, 9, 10, 0);

        // when & then
        assertThatThrownBy(() -> Account.reconstitute(
                        ACCOUNT_ID,
                        ACCOUNT_NUMBER,
                        CUSTOMER_ID,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        0L,
                        AccountStatus.CLOSED,
                        PASSWORD_HASH,
                        0,
                        false,
                        null,
                        null,
                        false,
                        null,
                        openedDate,
                        null,
                        closedDate,
                        null,
                        VERSION,
                        CREATED_AT,
                        UPDATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("해지 시각은 개설 시각보다 이전일 수 없습니다.");
    }

    @Test
    @DisplayName("비밀번호 실패 횟수가 5회이고 잠금 상태이면 정상적으로 복원한다")
    void reconstituteLockedAccount() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        // when
        Account account = Account.reconstitute(
                ACCOUNT_ID,
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                0L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                5,
                true,
                null,
                null,
                false,
                null,
                openedDate,
                null,
                null,
                null,
                VERSION,
                CREATED_AT,
                UPDATED_AT);

        // then
        assertThat(account.getPasswordFailureCount()).isEqualTo(5);
        assertThat(account.isPasswordLocked()).isTrue();
    }

    @Test
    @DisplayName("비밀번호 실패 횟수가 5회인데 잠금 상태가 아니면 복원할 수 없다")
    void failToReconstituteAccountWhenPasswordLockStateIsInvalid() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        // when & then
        assertThatThrownBy(() -> Account.reconstitute(
                        ACCOUNT_ID,
                        ACCOUNT_NUMBER,
                        CUSTOMER_ID,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        0L,
                        AccountStatus.ACTIVE,
                        PASSWORD_HASH,
                        5,
                        false,
                        null,
                        null,
                        false,
                        null,
                        openedDate,
                        null,
                        null,
                        null,
                        VERSION,
                        CREATED_AT,
                        UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("숫자 12자리가 아닌 계좌번호는 복원할 수 없다")
    void failToReconstituteAccountWithInvalidAccountNumber() {
        // given
        String invalidAccountNumber = "08810000001A";

        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        // when & then
        assertThatThrownBy(() -> Account.reconstitute(
                        ACCOUNT_ID,
                        invalidAccountNumber,
                        CUSTOMER_ID,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        0L,
                        AccountStatus.ACTIVE,
                        PASSWORD_HASH,
                        0,
                        false,
                        null,
                        null,
                        false,
                        null,
                        openedDate,
                        null,
                        null,
                        null,
                        VERSION,
                        CREATED_AT,
                        UPDATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌번호는 숫자 12자리여야 합니다.");
    }

    @Test
    @DisplayName("만기일이 개설일과 같아도 개설 시각이 자정 이후이면 계좌 복원에 실패한다")
    void failToReconstituteAccountWhenMaturityDateIsSameDateButBeforeOpenedTime() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        LocalDate maturityDate = LocalDate.of(2026, 8, 10);

        // when & then
        assertThatThrownBy(() -> Account.reconstitute(
                        ACCOUNT_ID,
                        ACCOUNT_NUMBER,
                        CUSTOMER_ID,
                        1L,
                        AccountType.TIME_DEPOSIT,
                        0L,
                        AccountStatus.ACTIVE,
                        PASSWORD_HASH,
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
                        VERSION,
                        CREATED_AT,
                        UPDATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("만기일은 개설일보다 이전일 수 없습니다.");
    }

    @Test
    @DisplayName("계좌별명을 등록할 수 있다")
    void changeAlias() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        // when
        account.changeAlias("생활비통장");

        // then
        assertThat(account.getAlias()).isEqualTo("생활비통장");
    }

    @Test
    @DisplayName("기존 계좌별명을 새로운 별명으로 변경할 수 있다")
    void changeExistingAlias() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        account.changeAlias("생활비");

        // when
        account.changeAlias("급여통장");

        // then
        assertThat(account.getAlias()).isEqualTo("급여통장");
    }

    @Test
    @DisplayName("계좌별명의 앞뒤 공백은 제거한다")
    void trimAlias() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        // when
        account.changeAlias("  생활비통장  ");

        // then
        assertThat(account.getAlias()).isEqualTo("생활비통장");
    }

    @Test
    @DisplayName("계좌별명을 삭제할 수 있다")
    void removeAlias() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        account.changeAlias("생활비통장");

        // when
        account.removeAlias();

        // then
        assertThat(account.getAlias()).isNull();
    }

    @Test
    @DisplayName("한글 계좌별명은 12자까지 허용한다")
    void allowTwelveKoreanCharacters() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        String alias = "가나다라마바사아자차카타";

        // when
        account.changeAlias(alias);

        // then
        assertThat(account.getAlias()).isEqualTo(alias);
    }

    @Test
    @DisplayName("한글 계좌별명이 12자를 초과하면 ACC0001을 발생시킨다")
    void rejectMoreThanTwelveKoreanCharacters() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        String alias = "가나다라마바사아자차카타파";

        // when
        BusinessException exception = catchThrowableOfType(() -> account.changeAlias(alias), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.INVALID_ACCOUNT_ALIAS);
    }

    @Test
    @DisplayName("영숫자 계좌별명은 24자까지 허용한다")
    void allowTwentyFourAlphaNumericCharacters() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        String alias = "abcdefghijklmnopqrstuvwx";

        // when
        account.changeAlias(alias);

        // then
        assertThat(account.getAlias()).isEqualTo(alias);
    }

    @Test
    @DisplayName("계좌별명이 24자를 초과하면 ACC0001을 발생시킨다")
    void rejectMoreThanTwentyFourCharacters() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        String alias = "abcdefghijklmnopqrstuvwxy";

        // when
        BusinessException exception = catchThrowableOfType(() -> account.changeAlias(alias), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.INVALID_ACCOUNT_ALIAS);
    }

    @Test
    @DisplayName("계좌별명이 공백뿐이면 필수 입력값 누락 오류가 발생한다")
    void rejectBlankAlias() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        // when
        BusinessException exception = catchThrowableOfType(() -> account.changeAlias("   "), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("계좌별명이 null이면 필수 입력값 누락 오류가 발생한다")
    void rejectNullAlias() {
        // given
        Account account = Account.open(
                ACCOUNT_NUMBER, CUSTOMER_ID, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, OPENED_DATE, null);

        // when
        BusinessException exception = catchThrowableOfType(() -> account.changeAlias(null), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }
}
