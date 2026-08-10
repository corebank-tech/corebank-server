package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
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

@DisplayName("Transfer 도메인 단위 테스트")
class TransferTest {

    @Nested
    @DisplayName("create 팩토리 메서드")
    class CreateTest {

        @Test
        @DisplayName("정상적인 이체 정보 입력 시 PROCESSING 상태의 Transfer 도메인이 생성된다")
        void createTransferSuccessfully() {
            // given
            String txNo = "20260809WB0000000001";
            Long withdrawalAccountId = 101L;
            Long depositAccountId = 202L;
            String depositAccountNumber = "110222222222";
            String payeeName = "성춘향";
            long amount = 10000L;
            long fee = 0L;
            TransferType transferType = TransferType.IMMEDIATE;
            TransferChannel channel = TransferChannel.WB;
            LocalDateTime now = LocalDateTime.of(2026, 8, 9, 12, 0, 0);

            // when
            Transfer transfer = Transfer.create(
                    txNo,
                    withdrawalAccountId,
                    depositAccountId,
                    depositAccountNumber,
                    payeeName,
                    amount,
                    fee,
                    transferType,
                    channel,
                    null,
                    null,
                    "출금메모",
                    "입금메모",
                    now
            );

            // then
            assertThat(transfer).isNotNull();
            assertThat(transfer.getTransactionNumber()).isEqualTo(txNo);
            assertThat(transfer.getWithdrawalAccountId()).isEqualTo(withdrawalAccountId);
            assertThat(transfer.getDepositAccountId()).isEqualTo(depositAccountId);
            assertThat(transfer.getAmount()).isEqualTo(amount);
            assertThat(transfer.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
            assertThat(transfer.getCreatedAt()).isEqualTo(now);
            assertThat(transfer.getTransferredAt()).isEqualTo(now);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -10000L})
        @DisplayName("이체 금액이 0 이하(0, 음수)이면 BusinessException(INVALID_AMOUNT) 예외가 발생한다")
        void throwsExceptionWhenAmountIsZeroOrNegative(long invalidAmount) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L,
                    202L,
                    "110222222222",
                    "성춘향",
                    invalidAmount,
                    0L,
                    TransferType.IMMEDIATE,
                    TransferChannel.WB,
                    null, null,
                    "출금메모", "입금메모",
                    now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
        }

        @Test
        @DisplayName("출금 계좌 ID가 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenWithdrawalAccountIdIsNull() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    null,
                    202L,
                    "110222222222",
                    "성춘향",
                    10000L,
                    0L,
                    TransferType.IMMEDIATE,
                    TransferChannel.WB,
                    null, null,
                    "출금메모", "입금메모",
                    now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("입금 계좌 ID가 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenDepositAccountIdIsNull() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L,
                    null,
                    "110222222222",
                    "성춘향",
                    10000L,
                    0L,
                    TransferType.IMMEDIATE,
                    TransferChannel.WB,
                    null, null,
                    "출금메모", "입금메모",
                    now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("출금 계좌와 입금 계좌가 동일하면 BusinessException(SAME_ACCOUNT_TRANSFER) 예외가 발생한다")
        void throwsExceptionWhenWithdrawalAndDepositAccountAreSame() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L,
                    101L,
                    "110111111111",
                    "홍길동",
                    10000L,
                    0L,
                    TransferType.IMMEDIATE,
                    TransferChannel.WB,
                    null, null,
                    "출금메모", "입금메모",
                    now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("거래번호가 없으면(null/공백) BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenTransactionNumberIsBlank(String invalidTxNo) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    invalidTxNo,
                    101L, 202L, "110222222222", "성춘향",
                    10000L, 0L, TransferType.IMMEDIATE, TransferChannel.WB,
                    null, null, "출금메모", "입금메모", now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("입금계좌번호가 없으면(null/공백) BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenDepositAccountNumberIsBlank(String invalidAccountNumber) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L, 202L, invalidAccountNumber, "성춘향",
                    10000L, 0L, TransferType.IMMEDIATE, TransferChannel.WB,
                    null, null, "출금메모", "입금메모", now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("수취인명이 없으면(null/공백) BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenPayeeNameIsBlank(String invalidPayeeName) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L, 202L, "110222222222", invalidPayeeName,
                    10000L, 0L, TransferType.IMMEDIATE, TransferChannel.WB,
                    null, null, "출금메모", "입금메모", now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("이체 유형(transferType)이 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenTransferTypeIsNull() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L, 202L, "110222222222", "성춘향",
                    10000L, 0L, null, TransferChannel.WB,
                    null, null, "출금메모", "입금메모", now
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
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L, 202L, "110222222222", "성춘향",
                    10000L, 0L, TransferType.IMMEDIATE, null,
                    null, null, "출금메모", "입금메모", now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        @Test
        @DisplayName("거래 발생 시각(now)이 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenNowIsNull() {
            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L, 202L, "110222222222", "성춘향",
                    10000L, 0L, TransferType.IMMEDIATE, TransferChannel.WB,
                    null, null, "출금메모", "입금메모", null
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    @Nested
    @DisplayName("complete 이체 완료 메서드")
    class CompleteTest {

        @Test
        @DisplayName("이체 완료 시 상태가 SUCCESS로 변경되고 잔액 및 완료 시각(transferredAt)이 갱신된다")
        void completesTransferAndUpdatesTransferredAt() {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 8, 9, 12, 0, 0);
            Transfer transfer = Transfer.create(
                    "20260809WB0000000001",
                    101L, 202L, "110222222222", "성춘향",
                    10000L, 0L, TransferType.IMMEDIATE, TransferChannel.WB,
                    null, null, "출금메모", "입금메모", createdAt
            );

            LocalDateTime completedAt = createdAt.plusSeconds(5);

            // when
            transfer.complete(90000L, completedAt);

            // then
            assertThat(transfer.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
            assertThat(transfer.getWithdrawalBalanceAfter()).isEqualTo(90000L);
            assertThat(transfer.getTransferredAt()).isEqualTo(completedAt);
            assertThat(transfer.getCreatedAt()).isEqualTo(createdAt);
        }
    }
}
