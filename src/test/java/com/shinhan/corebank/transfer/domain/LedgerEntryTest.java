package com.shinhan.corebank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("LedgerEntry 도메인 단위 테스트")
class LedgerEntryTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 12);

    @Nested
    @DisplayName("forCredit — 상대 고객 계좌 없는 입금 1행")
    class ForCreditTest {

        @Test
        @DisplayName("transfer_id·reversal_id 없이 입금 방향 1행을 만들고 시각을 마이크로초로 자른다")
        void createsSingleDepositEntry() {
            // given
            LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 23, 3, 0, 0, 123456789);

            // when
            LedgerEntry entry = LedgerEntry.forCredit(
                    "20261023BT0000000002",
                    303L,
                    1_020_000L,
                    20_000L,
                    "INTEREST",
                    "이자",
                    TransferChannel.BT,
                    occurredAt,
                    TRADE_DATE);

            // then
            assertThat(entry.getLedgerEntryId()).isNull();
            assertThat(entry.getTransferId()).isNull();
            assertThat(entry.getReversalId()).isNull();
            assertThat(entry.isReversed()).isFalse();
            assertThat(entry.getAccountId()).isEqualTo(303L);
            assertThat(entry.getDirection()).isEqualTo(LedgerDirection.DEPOSIT);
            assertThat(entry.getAmount()).isEqualTo(20_000L);
            assertThat(entry.getBalanceAfter()).isEqualTo(1_020_000L);
            assertThat(entry.getTransactionType()).isEqualTo("INTEREST");
            assertThat(entry.getTransactionContent()).isEqualTo("이자");
            assertThat(entry.getChannel()).isEqualTo(TransferChannel.BT);
            assertThat(entry.getOccurredAt()).isEqualTo(occurredAt.truncatedTo(ChronoUnit.MICROS));
            assertThat(entry.getTradeDate()).isEqualTo(TRADE_DATE);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("금액이 0 이하이면 BusinessException(INVALID_AMOUNT) 예외가 발생한다")
        void throwsExceptionWhenAmountIsNotPositive(long invalidAmount) {
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002",
                            303L,
                            1_000_000L,
                            invalidAmount,
                            "INTEREST",
                            "이자",
                            TransferChannel.BT,
                            LocalDateTime.now(),
                            TRADE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("거래번호나 거래유형이 없으면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenRequiredTextIsBlank(String blank) {
            LocalDateTime now = LocalDateTime.now();

            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            blank, 303L, 1_020_000L, 20_000L, "INTEREST", "이자", TransferChannel.BT, now, TRADE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002",
                            303L,
                            1_020_000L,
                            20_000L,
                            blank,
                            "이자",
                            TransferChannel.BT,
                            now,
                            TRADE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("계좌 ID·채널·시각이 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenRequiredValueIsNull() {
            LocalDateTime now = LocalDateTime.now();

            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002",
                            null,
                            1_020_000L,
                            20_000L,
                            "INTEREST",
                            "이자",
                            TransferChannel.BT,
                            now,
                            TRADE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002", 303L, 1_020_000L, 20_000L, "INTEREST", "이자", null, now, TRADE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002",
                            303L,
                            1_020_000L,
                            20_000L,
                            "INTEREST",
                            "이자",
                            TransferChannel.BT,
                            null,
                            TRADE_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002",
                            303L,
                            1_020_000L,
                            20_000L,
                            "INTEREST",
                            "이자",
                            TransferChannel.BT,
                            now,
                            null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    @Nested
    @DisplayName("타행 출금 1행 · 되돌림 입금 1행 (forDebit · forRefund)")
    class InterbankTest {

        private final LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 23, 10, 30, 15);
        private final LocalDate tradeDate = LocalDate.of(2026, 10, 23);

        // 저장 뒤 읽어 온 출금 행처럼 ID를 채운다.
        private LedgerEntry postedDebit(LedgerDirection direction, boolean reversed) {
            return LedgerEntry.builder()
                    .ledgerEntryId(7001L)
                    .transferId(900L)
                    .accountId(101L)
                    .transactionNumber("20261023WB0000000123")
                    .direction(direction)
                    .amount(50000L)
                    .balanceAfter(950000L)
                    .transactionType("INTERBANK_TRANSFER")
                    .transactionContent("생활비")
                    .channel(TransferChannel.WB)
                    .reversed(reversed)
                    .occurredAt(occurredAt)
                    .tradeDate(tradeDate)
                    .build();
        }

        private LedgerEntry postedDebit() {
            return postedDebit(LedgerDirection.WITHDRAWAL, false);
        }

        @Test
        @DisplayName("forDebit은 상대 계좌 없이 출금(WITHDRAWAL) 1행을 만든다")
        void forDebit_createsWithdrawal() {
            LedgerEntry debit = LedgerEntry.forDebit(
                    900L,
                    "20261023WB0000000123",
                    101L,
                    950000L,
                    50000L,
                    "INTERBANK_TRANSFER",
                    "생활비",
                    TransferChannel.WB,
                    occurredAt,
                    tradeDate);

            assertThat(debit.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
            assertThat(debit.getTransferId()).isEqualTo(900L);
            assertThat(debit.getAmount()).isEqualTo(50000L);
            assertThat(debit.getReversalId()).isNull();
        }

        @Test
        @DisplayName("forRefund는 같은 계좌·거래번호·금액의 입금 1행을 만들고 reversal_id로 원 출금을 가리킨다")
        void forRefund_linksOriginalDebit() {
            LedgerEntry original = postedDebit();

            LedgerEntry refund =
                    LedgerEntry.forRefund(original, 1000000L, "타행이체반환", occurredAt.plusMinutes(1), tradeDate);

            assertThat(refund.getDirection()).isEqualTo(LedgerDirection.DEPOSIT);
            assertThat(refund.getAccountId()).isEqualTo(101L);
            assertThat(refund.getTransactionNumber()).isEqualTo("20261023WB0000000123");
            assertThat(refund.getAmount()).isEqualTo(50000L);
            assertThat(refund.getReversalId()).isEqualTo(7001L);
            assertThat(refund.getTransactionType()).isEqualTo("INTERBANK_TRANSFER");
        }

        @Test
        @DisplayName("입금 행이나 이미 되돌린 출금은 되돌릴 수 없다")
        void forRefund_rejectsDepositOrReversed() {
            LedgerEntry reversed = postedDebit(LedgerDirection.WITHDRAWAL, true);
            LedgerEntry deposit = postedDebit(LedgerDirection.DEPOSIT, false);

            assertThatThrownBy(() -> LedgerEntry.forRefund(reversed, 0L, null, occurredAt, tradeDate))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
            assertThatThrownBy(() -> LedgerEntry.forRefund(deposit, 0L, null, occurredAt, tradeDate))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
        }
    }
}
