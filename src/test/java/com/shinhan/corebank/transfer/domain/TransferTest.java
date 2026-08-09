package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    }
}
