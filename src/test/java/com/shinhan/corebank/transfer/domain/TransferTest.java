package com.shinhan.corebank.transfer.domain;

import java.time.LocalDate;
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

    private static Transfer newProcessingTransfer() {
        return Transfer.create(
                "20260809WB0000000001",
                101L, 202L, "110222222222", "성춘향",
                10000L, 0L, TransferType.IMMEDIATE, TransferChannel.WB,
                null, null, null, "출금메모", "입금메모", LocalDateTime.of(2026, 8, 9, 12, 0, 0)
        );
    }

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
                    null, null, null,
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

        @Test
        @DisplayName("executionDate가 함께 전달되면 Transfer 도메인에 그대로 저장된다")
        void createTransfer_StoresExecutionDate() {
            // given
            LocalDate executionDate = LocalDate.of(2026, 8, 20);
            LocalDateTime now = LocalDateTime.of(2026, 8, 20, 9, 0, 0);

            // when
            Transfer transfer = Transfer.create(
                    "20260820AT0000000001",
                    101L, 202L, "110222222222", "성춘향",
                    10000L, 0L, TransferType.AUTO, TransferChannel.BT,
                    TransferSourceType.AUTO, 55L, executionDate,
                    "출금메모", "입금메모", now
            );

            // then
            assertThat(transfer.getExecutionDate()).isEqualTo(executionDate);
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
                    null, null, null,
                    "출금메모", "입금메모",
                    now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, -10000L})
        @DisplayName("수수료(fee)가 음수이면 BusinessException(INVALID_INPUT) 예외가 발생한다")
        void throwsExceptionWhenFeeIsNegative(long invalidFee) {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> Transfer.create(
                    "20260809WB0000000001",
                    101L,
                    202L,
                    "110222222222",
                    "성춘향",
                    10000L,
                    invalidFee,
                    TransferType.IMMEDIATE,
                    TransferChannel.WB,
                    null, null, null,
                    "출금메모", "입금메모",
                    now
            ))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);
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
                    null, null, null,
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
                    null, null, null,
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
                    null, null, null,
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
                    null, null, null, "출금메모", "입금메모", now
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
                    null, null, null, "출금메모", "입금메모", now
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
                    null, null, null, "출금메모", "입금메모", now
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
                    null, null, null, "출금메모", "입금메모", now
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
                    10000L, 0L, TransferType.IMMEDIATE, null, null, null, null, "출금메모", "입금메모", now
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
                    null, null, null, "출금메모", "입금메모", null
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
                    null, null, null, "출금메모", "입금메모", createdAt
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

        @Test
        @DisplayName("completedAt이 null이면 BusinessException(REQUIRED_FIELD_MISSING) 예외가 발생한다")
        void throwsExceptionWhenCompletedAtIsNull() {
            // given
            Transfer transfer = newProcessingTransfer();

            // when & then
            assertThatThrownBy(() -> transfer.complete(90000L, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    @Nested
    @DisplayName("이체 상태 전이 제약")
    class StatusTransitionTest {

        @Test
        @DisplayName("이체 실패 처리 시 상태가 ERROR로 변경된다")
        void failsTransferSuccessfully() {
            // given
            Transfer transfer = newProcessingTransfer();

            // when
            transfer.fail("TRF9999", "잔액 부족");

            // then
            assertThat(transfer.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
            assertThat(transfer.getErrorCode()).isEqualTo("TRF9999");
            assertThat(transfer.getErrorMessage()).isEqualTo("잔액 부족");
        }

        @Test
        @DisplayName("이미 완료(SUCCESS)된 이체를 다시 complete()하면 BusinessException(INVALID_STATUS_TRANSITION) 예외가 발생한다")
        void throwsExceptionWhenCompletingAlreadyCompletedTransfer() {
            // given
            Transfer transfer = newProcessingTransfer();
            transfer.complete(90000L, LocalDateTime.now());

            // when & then
            assertThatThrownBy(() -> transfer.complete(90000L, LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("이미 완료(SUCCESS)된 이체를 fail() 처리하면 BusinessException(INVALID_STATUS_TRANSITION) 예외가 발생한다")
        void throwsExceptionWhenFailingAlreadyCompletedTransfer() {
            // given
            Transfer transfer = newProcessingTransfer();
            transfer.complete(90000L, LocalDateTime.now());

            // when & then
            assertThatThrownBy(() -> transfer.fail("TRF9999", "잔액 부족"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("이미 실패(ERROR)한 이체를 complete() 처리하면 BusinessException(INVALID_STATUS_TRANSITION) 예외가 발생한다")
        void throwsExceptionWhenCompletingAlreadyFailedTransfer() {
            // given
            Transfer transfer = newProcessingTransfer();
            transfer.fail("TRF9999", "잔액 부족");

            // when & then
            assertThatThrownBy(() -> transfer.complete(90000L, LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TransferErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
