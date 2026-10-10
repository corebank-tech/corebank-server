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
}
