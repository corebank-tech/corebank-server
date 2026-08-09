package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        @DisplayName("기표 금액이 0 이하이면 IllegalArgumentException 예외가 발생한다")
        void throwsExceptionWhenAmountIsZeroOrNegative() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    "20260809WB0000000001",
                    101L, 90000L,
                    202L, 110000L,
                    0L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0보다 커야 합니다");
        }

        @Test
        @DisplayName("출금 계좌와 입금 계좌가 동일하면 IllegalArgumentException 예외가 발생한다")
        void throwsExceptionWhenWithdrawalAndDepositAccountAreSame() {
            // given
            LocalDateTime now = LocalDateTime.now();

            // when & then
            assertThatThrownBy(() -> LedgerPair.forTransfer(
                    "20260809WB0000000001",
                    101L, 90000L,
                    101L, 110000L,
                    10000L,
                    "IMMEDIATE_TRANSFER",
                    "메모", "메모",
                    TransferChannel.WB, now
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("동일할 수 없습니다");
        }
    }
}
