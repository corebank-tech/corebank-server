package com.shinhan.corebank.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferResponseTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 6, 19, 0);

    @Test
    @DisplayName("TIMEOUT 결과에 생성 시각이 실려 와도 transferredAt은 null로 내려간다")
    void from_timeout_hidesTransferredAt() {
        TransferResult result = TransferResult.builder()
                .status(ProcessResultStatus.TIMEOUT)
                .transactionNumber("20261006WB0000000001")
                .transferredAt(CREATED_AT)
                .build();

        TransferResponse response = TransferResponse.from(result);

        assertThat(response.transferredAt()).isNull();
        assertThat(response.transactionNumber()).isEqualTo("20261006WB0000000001");
    }

    @Test
    @DisplayName("SUCCESS 결과의 transferredAt은 그대로 내려간다")
    void from_success_keepsTransferredAt() {
        TransferResult result = TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20261006WB0000000001")
                .transferredAt(CREATED_AT)
                .withdrawalBalanceAfter(90_000L)
                .build();

        TransferResponse response = TransferResponse.from(result);

        assertThat(response.transferredAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("보류 결과면 status·transactionNumber가 null이고 transferHold에 보류 ID·실행 예정·취소 가능 시각이 담긴다")
    void held_mapsTransferHold() {
        LocalDateTime releaseAt = LocalDateTime.of(2031, 7, 1, 13, 0);
        TransferResult held = TransferResult.builder()
                .transferHoldId(7L)
                .releaseAt(releaseAt)
                .cancellableUntil(releaseAt.minusMinutes(30))
                .build();

        TransferResponse response = TransferResponse.from(held);

        assertThat(response.status()).isNull();
        assertThat(response.transactionNumber()).isNull();
        assertThat(response.transferHold())
                .isEqualTo(new TransferHoldResponse(7L, "HELD", releaseAt, releaseAt.minusMinutes(30)));
    }

    @Test
    @DisplayName("보류가 아니면 transferHold는 null이다")
    void notHeld_hasNoTransferHold() {
        TransferResult success = TransferResult.builder()
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20310701WB0000000001")
                .build();

        assertThat(TransferResponse.from(success).transferHold()).isNull();
    }
}
