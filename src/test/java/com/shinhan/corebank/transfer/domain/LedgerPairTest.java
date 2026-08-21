package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LedgerPair 도메인 단위 테스트")
class LedgerPairTest {

    @Nested
    @DisplayName("forTransfer 팩토리 메서드")
    class ForTransferTest {

        @Test
        @DisplayName("정상적인 이체 정보 입력 시 출금 1행과 입금 1행 원장 쌍이 생성된다")
        void createsWithdrawalAndDepositPairSuccessfully() {
            // given
            Long transferId = 500L;
            String txNo = "20260809WB0000000001";
            Long withdrawalAccountId = 101L;
            long withdrawalBalanceAfter = 90000L;
            Long depositAccountId = 202L;
            long depositBalanceAfter = 110000L;
            long amount = 10000L;
            String txType = "IMMEDIATE_TRANSFER";
            String myMemo = "홍길동이체";
            String recipientMemo = "성춘향입금";
            TransferChannel channel = TransferChannel.WB;
            LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 9, 12, 0, 0);

            // when
            LedgerPair pair = LedgerPair.forTransfer(
                    transferId,
                    txNo,
                    withdrawalAccountId,
                    withdrawalBalanceAfter,
                    depositAccountId,
                    depositBalanceAfter,
                    amount,
                    txType,
                    myMemo,
                    recipientMemo,
                    channel,
                    occurredAt
            );

            // then
            assertThat(pair).isNotNull();

            // 출금행 검증
            LedgerEntry withdrawal = pair.getWithdrawalEntry();
            assertThat(withdrawal.getTransferId()).isEqualTo(transferId);
            assertThat(withdrawal.getAccountId()).isEqualTo(withdrawalAccountId);
            assertThat(withdrawal.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
            assertThat(withdrawal.getAmount()).isEqualTo(amount);
            assertThat(withdrawal.getBalanceAfter()).isEqualTo(withdrawalBalanceAfter);
            assertThat(withdrawal.getTransactionNumber()).isEqualTo(txNo);
            assertThat(withdrawal.getTransactionContent()).isEqualTo(myMemo);
            assertThat(withdrawal.getChannel()).isEqualTo(channel);
            assertThat(withdrawal.getOccurredAt()).isEqualTo(occurredAt);

            // 입금행 검증
            LedgerEntry deposit = pair.getDepositEntry();
            assertThat(deposit.getTransferId()).isEqualTo(transferId);
            assertThat(deposit.getAccountId()).isEqualTo(depositAccountId);
            assertThat(deposit.getDirection()).isEqualTo(LedgerDirection.DEPOSIT);
            assertThat(deposit.getAmount()).isEqualTo(amount);
            assertThat(deposit.getBalanceAfter()).isEqualTo(depositBalanceAfter);
            assertThat(deposit.getTransactionNumber()).isEqualTo(txNo);
            assertThat(deposit.getTransactionContent()).isEqualTo(recipientMemo);
            assertThat(deposit.getChannel()).isEqualTo(channel);
            assertThat(deposit.getOccurredAt()).isEqualTo(occurredAt);
        }

        @Test
        @DisplayName("occurredAt에 나노초가 포함되어도 마이크로초(MICROS) 단위로 절삭되어 저장된다")
        void truncatesOccurredAtToMicroseconds() {
            // given
            LocalDateTime nanoTime = LocalDateTime.of(2026, 8, 9, 12, 0, 0, 123456789);

            // when
            LedgerPair pair = LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB,
                    nanoTime
            );

            // then
            LocalDateTime expectedMicroTime = nanoTime.truncatedTo(ChronoUnit.MICROS);
            assertThat(pair.getWithdrawalEntry().getOccurredAt()).isEqualTo(expectedMicroTime);
            assertThat(pair.getDepositEntry().getOccurredAt()).isEqualTo(expectedMicroTime);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -10000L})
        @DisplayName("기표 금액이 0 이하(0, 음수)이면 BusinessException(INVALID_AMOUNT) 예외가 발생한다")
        void throwsExceptionWhenAmountIsZeroOrNegative(long invalidAmount) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    invalidAmount,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
        }

        @Test
        @DisplayName("출금 계좌와 입금 계좌가 동일하면 BusinessException(SAME_ACCOUNT_TRANSFER) 예외가 발생한다")
        void throwsExceptionWhenWithdrawalAndDepositAccountAreSame() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    101L, 90000L,
                    101L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
        }

        @Test
        @DisplayName("transferId가 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenTransferIdIsNull() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    null,
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("출금 계좌 ID 또는 입금 계좌 ID가 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenAccountIdIsNull() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    null, 90000L,
                    202L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("occurredAt이 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenOccurredAtIsNull() {
            // given & when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB,
                    null
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("거래번호가 없으면(null/공백) BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenTransactionNumberIsBlank(String invalidTxNo) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    invalidTxNo,
                    101L, 90000L,
                    202L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("거래유형(transactionType)이 없으면(null/공백) BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenTransactionTypeIsBlank(String invalidTxType) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    10000L,
                    invalidTxType,
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("채널(channel)이 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenChannelIsNull() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    500L,
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    null, now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    @Nested
    @DisplayName("forProductSubscription 팩토리 메서드")
    class ForProductSubscriptionTest {

        @Test
        @DisplayName("상품가입 초입금은 transfer_id 없이 PRODUCT_SUBSCRIPTION 유형의 원장 쌍이 생성된다")
        void createsPairWithoutTransferId() {
            // given
            String txNo = "20260821WB0000000001";
            Long withdrawalAccountId = 101L;
            Long depositAccountId = 202L;
            LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 21, 12, 0, 0);

            // when
            LedgerPair pair = LedgerPair.forProductSubscription(
                    txNo,
                    withdrawalAccountId, 9_500_000L,
                    depositAccountId, 500_000L,
                    500_000L,
                    "상품가입", "상품가입",
                    TransferChannel.WB, occurredAt);

            // then
            LedgerEntry withdrawal = pair.getWithdrawalEntry();
            assertThat(withdrawal.getTransferId()).isNull();
            assertThat(withdrawal.getTransactionType()).isEqualTo("PRODUCT_SUBSCRIPTION");
            assertThat(withdrawal.getAccountId()).isEqualTo(withdrawalAccountId);
            assertThat(withdrawal.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
            assertThat(withdrawal.getBalanceAfter()).isEqualTo(9_500_000L);

            LedgerEntry deposit = pair.getDepositEntry();
            assertThat(deposit.getTransferId()).isNull();
            assertThat(deposit.getTransactionType()).isEqualTo("PRODUCT_SUBSCRIPTION");
            assertThat(deposit.getAccountId()).isEqualTo(depositAccountId);
            assertThat(deposit.getDirection()).isEqualTo(LedgerDirection.DEPOSIT);
            assertThat(deposit.getBalanceAfter()).isEqualTo(500_000L);

            assertThat(withdrawal.getTransactionNumber()).isEqualTo(deposit.getTransactionNumber());
            assertThat(withdrawal.getOccurredAt()).isEqualTo(deposit.getOccurredAt());
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("초입금액이 0 이하이면 BusinessException(INVALID_AMOUNT) 예외가 발생한다")
        void throwsExceptionWhenAmountIsNotPositive(long invalidAmount) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forProductSubscription(
                    "20260821WB0000000001",
                    101L, 9_500_000L,
                    202L, 500_000L,
                    invalidAmount,
                    "상품가입", "상품가입",
                    TransferChannel.WB, now))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("거래번호가 없으면(null/공백) BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenTransactionNumberIsBlank(String invalidTxNo) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forProductSubscription(
                    invalidTxNo,
                    101L, 9_500_000L,
                    202L, 500_000L,
                    500_000L,
                    "상품가입", "상품가입",
                    TransferChannel.WB, now))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
