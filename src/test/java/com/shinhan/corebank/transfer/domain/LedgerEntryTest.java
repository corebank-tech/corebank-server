package com.shinhan.corebank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
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
                    occurredAt);

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
                            LocalDateTime.now()))
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
                            blank, 303L, 1_020_000L, 20_000L, "INTEREST", "이자", TransferChannel.BT, now))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002", 303L, 1_020_000L, 20_000L, blank, "이자", TransferChannel.BT, now))
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
                            now))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
            assertThatThrownBy(() -> LedgerEntry.forCredit(
                            "20261023BT0000000002", 303L, 1_020_000L, 20_000L, "INTEREST", "이자", null, now))
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
                            null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
