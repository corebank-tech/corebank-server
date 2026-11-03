package com.shinhan.corebank.transfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferResultTest {

    @Test
    @DisplayName("고객이 조치할 수 있는 실패 코드는 그 코드의 문구를 고객용 실패 사유로 돌려준다")
    void customerActionableErrorCode_returnsErrorCodeMessage() {
        // given
        TransferResult result = failedWith(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());

        // when & then
        assertThat(result.customerFailureReason()).hasValue(TransferErrorCode.INSUFFICIENT_BALANCE.getMessage());
    }

    @Test
    @DisplayName("고객이 조치할 수 없는 코드 · 다른 도메인 코드 · 코드 없음은 사유를 돌려주지 않는다")
    void otherOrMissingErrorCode_returnsNoReason() {
        // when & then
        assertThat(failedWith(TransferErrorCode.SAME_ACCOUNT_TRANSFER.getCode()).customerFailureReason())
                .isEmpty();
        assertThat(failedWith("CMN9999").customerFailureReason()).isEmpty();
        assertThat(failedWith(null).customerFailureReason()).isEmpty();
    }

    @Test
    @DisplayName("성공한 결과는 조치 가능한 코드가 남아 있어도 실패 사유를 돌려주지 않는다")
    void succeededResult_returnsNoReasonEvenWithActionableErrorCode() {
        // given
        TransferResult result = TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .errorCode(TransferErrorCode.INSUFFICIENT_BALANCE.getCode())
                .build();

        // when & then
        assertThat(result.customerFailureReason()).isEmpty();
    }

    private static TransferResult failedWith(String errorCode) {
        return TransferResult.builder()
                .status(ProcessResultStatus.ERROR)
                .errorCode(errorCode)
                .build();
    }
}
