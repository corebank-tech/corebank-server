package com.shinhan.corebank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountPaymentStopTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime STOPPED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);

    private static Account account(AccountStatus status, long balance, long holdAmount) {
        return Account.reconstitute(
                101L,
                "088100000001",
                1L,
                null,
                AccountType.DEMAND_DEPOSIT,
                balance,
                holdAmount,
                status,
                "test-password-hash",
                0,
                false,
                null,
                null,
                false,
                null,
                OPENED_AT,
                null,
                status == AccountStatus.CLOSED ? OPENED_AT.plusDays(1) : null,
                null,
                0L,
                OPENED_AT,
                OPENED_AT);
    }

    @Nested
    @DisplayName("계좌에 지급정지 반영")
    class ApplyToAccount {

        @Test
        @DisplayName("전부 지급정지가 있으면 SUSPENDED가 되고 보류액은 일부 지급정지 합계다")
        void fullStopSuspends() {
            Account account = account(AccountStatus.ACTIVE, 100_000L, 0L);

            account.applyPaymentStops(AccountStatus.ACTIVE, true, 30_000L);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(account.getHoldAmount()).isEqualTo(30_000L);
            assertThat(account.getAvailableBalance()).isZero();
        }

        @Test
        @DisplayName("일부 지급정지만 있으면 정지 전 상태로 두고 보류액만 묶는다")
        void partialStopHoldsOnly() {
            Account account = account(AccountStatus.SUSPENDED, 100_000L, 0L);

            account.applyPaymentStops(AccountStatus.ACTIVE, false, 30_000L);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.getHoldAmount()).isEqualTo(30_000L);
            assertThat(account.getAvailableBalance()).isEqualTo(70_000L);
        }

        @Test
        @DisplayName("정지 전 상태가 SUSPENDED면 일부 지급정지로 바뀌어도 SUSPENDED를 유지한다")
        void keepsOriginalSuspension() {
            Account account = account(AccountStatus.SUSPENDED, 100_000L, 0L);

            account.applyPaymentStops(AccountStatus.SUSPENDED, false, 30_000L);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }

        @Test
        @DisplayName("보류액이 원장잔액보다 크면 거부한다")
        void rejectsHoldOverBalance() {
            Account account = account(AccountStatus.ACTIVE, 10_000L, 0L);

            assertThatThrownBy(() -> account.applyPaymentStops(AccountStatus.ACTIVE, false, 10_001L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @EnumSource(
                value = AccountStatus.class,
                names = {"MATURED", "CLOSED", "DORMANT"})
        @DisplayName("ACTIVE, SUSPENDED 외 상태에는 지급정지를 걸 수 없다")
        void rejectsOtherStatus(AccountStatus status) {
            Account account = account(status, 10_000L, 0L);

            assertThatThrownBy(account::validatePaymentStopAllowed).isInstanceOf(BusinessException.class);
        }

        @ParameterizedTest
        @EnumSource(
                value = AccountStatus.class,
                names = {"ACTIVE", "SUSPENDED"})
        @DisplayName("ACTIVE, SUSPENDED 계좌에는 지급정지를 걸 수 있다")
        void allowsActiveOrSuspended(AccountStatus status) {
            Account account = account(status, 10_000L, 0L);

            account.validatePaymentStopAllowed();
        }

        @Test
        @DisplayName("입출금계좌가 아니면 지급정지를 걸 수 없다")
        void rejectsNonDemandDeposit() {
            Account account = Account.reconstitute(
                    102L,
                    "088200000001",
                    1L,
                    10L,
                    AccountType.TIME_DEPOSIT,
                    10_000L,
                    0L,
                    AccountStatus.ACTIVE,
                    "test-password-hash",
                    0,
                    false,
                    null,
                    null,
                    false,
                    null,
                    OPENED_AT,
                    OPENED_AT.toLocalDate().plusMonths(6),
                    null,
                    null,
                    0L,
                    OPENED_AT,
                    OPENED_AT);

            assertThatThrownBy(account::validatePaymentStopAllowed)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AccountErrorCode.PAYMENT_STOP_UNSUPPORTED_ACCOUNT_TYPE);
        }

        @ParameterizedTest
        @EnumSource(
                value = AccountStatus.class,
                names = {"MATURED", "DORMANT"})
        @DisplayName("다른 경로가 상태를 바꿨으면 지급정지 반영이 그 상태를 덮어쓰지 않고 거부한다")
        void rejectsOverwritingOtherStatus(AccountStatus status) {
            Account account = account(status, 100_000L, 30_000L);

            assertThatThrownBy(() -> account.applyPaymentStops(AccountStatus.ACTIVE, false, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_STATUS);
            assertThat(account.getStatus()).isEqualTo(status);
            assertThat(account.getHoldAmount()).isEqualTo(30_000L);
        }
    }

    @Nested
    @DisplayName("사건별 지급정지 기록")
    class StopRecord {

        @Test
        @DisplayName("전부 지급정지 기록은 금액이 0이고 유효 상태로 시작한다")
        void fullStopRecord() {
            AccountPaymentStop stop = AccountPaymentStop.full(101L, 7L, AccountStatus.ACTIVE, STOPPED_AT);

            assertThat(stop.getStopType()).isEqualTo(PaymentStopType.FULL);
            assertThat(stop.getRequestedAmount()).isZero();
            assertThat(stop.getAppliedAmount()).isZero();
            assertThat(stop.isActive()).isTrue();
            assertThat(stop.getReleasedAt()).isNull();
        }

        @Test
        @DisplayName("일부 지급정지로 바꾸면 요청액과 적용액을 남긴다")
        void convertToPartial() {
            AccountPaymentStop stop = AccountPaymentStop.full(101L, 7L, AccountStatus.ACTIVE, STOPPED_AT);

            stop.convertToPartial(50_000L, 30_000L);

            assertThat(stop.getStopType()).isEqualTo(PaymentStopType.PARTIAL);
            assertThat(stop.getRequestedAmount()).isEqualTo(50_000L);
            assertThat(stop.getAppliedAmount()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("적용액이 요청액보다 크면 거부한다")
        void rejectsAppliedOverRequested() {
            AccountPaymentStop stop = AccountPaymentStop.full(101L, 7L, AccountStatus.ACTIVE, STOPPED_AT);

            assertThatThrownBy(() -> stop.convertToPartial(10_000L, 10_001L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 일부 지급정지인 기록은 다시 바꿀 수 없다")
        void rejectsDoubleConversion() {
            AccountPaymentStop stop = AccountPaymentStop.full(101L, 7L, AccountStatus.ACTIVE, STOPPED_AT);
            stop.convertToPartial(50_000L, 30_000L);

            assertThatThrownBy(() -> stop.convertToPartial(50_000L, 30_000L)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("해제하면 유효하지 않게 되고 해제 시각을 남긴다")
        void release() {
            AccountPaymentStop stop = AccountPaymentStop.full(101L, 7L, AccountStatus.ACTIVE, STOPPED_AT);

            stop.release(STOPPED_AT.plusDays(3));

            assertThat(stop.isActive()).isFalse();
            assertThat(stop.getReleasedAt()).isEqualTo(STOPPED_AT.plusDays(3));
        }

        @Test
        @DisplayName("해제된 기록은 일부 지급정지로 바꿀 수 없다")
        void rejectsConversionAfterRelease() {
            AccountPaymentStop stop = AccountPaymentStop.full(101L, 7L, AccountStatus.ACTIVE, STOPPED_AT);
            stop.release(STOPPED_AT.plusDays(3));

            assertThatThrownBy(() -> stop.convertToPartial(50_000L, 30_000L)).isInstanceOf(IllegalStateException.class);
        }

        @ParameterizedTest
        @EnumSource(
                value = AccountStatus.class,
                names = {"MATURED", "CLOSED", "DORMANT"})
        @DisplayName("정지 전 상태는 ACTIVE나 SUSPENDED만 남는다")
        void rejectsStatusBefore(AccountStatus statusBefore) {
            assertThatThrownBy(() -> AccountPaymentStop.full(101L, 7L, statusBefore, STOPPED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
