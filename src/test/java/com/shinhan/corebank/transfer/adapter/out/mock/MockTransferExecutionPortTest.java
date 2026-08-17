package com.shinhan.corebank.transfer.adapter.out.mock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

class MockTransferExecutionPortTest {

    private final MockTransferExecutionPort mockPort = new MockTransferExecutionPort();

    @Test
    @DisplayName("Mock 객체는 무조건 SUCCESS 상태와 20자리의 거래번호를 반환해야 한다.")
    void execute_ShouldReturnSuccessWith20CharTransactionNumber() {
        // given
        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(1L)
                .depositAccountNumber("123456789012")
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .amount(10000L)
                .build();

        // when
        TransferResult result = mockPort.execute(command);

        // then
        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
        
        // 거래번호 검증 (YYYYMMDD(8) + WB(2) + 일련번호(10) = 20자리)
        assertThat(result.transactionNumber()).isNotNull();
        assertThat(result.transactionNumber()).matches("\\d{8}WB\\d{10}");
        
        // 잔액 반환 검증
        assertThat(result.withdrawalBalanceAfter()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("여러 번 호출 시 거래번호의 일련번호(AtomicLong)가 중복 없이 증가해야 한다.")
    void execute_ShouldIncrementSequence() {
        // given
        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(1L)
                .depositAccountNumber("123456789012")
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.BT)
                .amount(10000L)
                .build();

        // when
        TransferResult result1 = mockPort.execute(command);
        TransferResult result2 = mockPort.execute(command);
        TransferResult result3 = mockPort.execute(command);

        // then
        // 20자리 문자열의 마지막 10자리가 일련번호
        String seq1 = result1.transactionNumber().substring(10);
        String seq2 = result2.transactionNumber().substring(10);
        String seq3 = result3.transactionNumber().substring(10);

        // 결과는 1씩 증가해야 하며 서로 달라야 함
        assertThat(seq1).isNotEqualTo(seq2);
        assertThat(seq2).isNotEqualTo(seq3);
        
        // 형변환 이후 크기 비교(1씩 증가하고 있는지 여부)
        long seqNum1 = Long.parseLong(seq1);
        long seqNum2 = Long.parseLong(seq2);
        long seqNum3 = Long.parseLong(seq3);
        
        assertThat(seqNum2).isEqualTo(seqNum1 + 1);
        assertThat(seqNum3).isEqualTo(seqNum2 + 1);
    }
}
