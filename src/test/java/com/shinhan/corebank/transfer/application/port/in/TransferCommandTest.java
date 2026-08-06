package com.shinhan.corebank.transfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

class TransferCommandTest {

    @Test
    @DisplayName("필수값이 누락되면 REQUIRED_FIELD_MISSING 에러가 발생한다.")
    void missingRequiredFields_ThrowsException() {
        assertThatThrownBy(() -> TransferCommand.builder()
                .amount(10000L)
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.REQUIRED_FIELD_MISSING.getMessage());
    }

    @Test
    @DisplayName("계좌번호가 12자리 숫자가 아니면 INVALID_INPUT 에러가 발생한다.")
    void invalidAccountNumberFormat_ThrowsException() {
        assertThatThrownBy(() -> TransferCommand.builder()
                .withdrawalAccountId(1L)
                .depositAccountNumber("12345678901") // 11자리
                .amount(10000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("즉시 이체인데 원본 식별자(sourceId)가 있으면 INVALID_INPUT 에러가 발생한다.")
    void immediateTransferWithSourceId_ThrowsException() {
        assertThatThrownBy(() -> TransferCommand.builder()
                .withdrawalAccountId(1L)
                .depositAccountNumber("123456789012")
                .amount(10000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .sourceId(100L) // 즉시 이체인데 sourceId 존재
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("예약/자동 이체인데 원본 식별자(sourceId)가 없으면 REQUIRED_FIELD_MISSING 에러가 발생한다.")
    void scheduledTransferWithoutSourceId_ThrowsException() {
        assertThatThrownBy(() -> TransferCommand.builder()
                .withdrawalAccountId(1L)
                .depositAccountNumber("123456789012")
                .amount(10000L)
                .transferType(TransferType.SCHEDULED)
                .channel(TransferChannel.WB)
                // sourceId 누락
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.REQUIRED_FIELD_MISSING.getMessage());
    }

    @Test
    @DisplayName("정상적인 즉시 이체 요청은 예외 없이 생성된다.")
    void validImmediateTransfer_Success() {
        assertDoesNotThrow(() -> TransferCommand.builder()
                .withdrawalAccountId(1L)
                .depositAccountNumber("123456789012")
                .amount(10000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build());
    }
}
