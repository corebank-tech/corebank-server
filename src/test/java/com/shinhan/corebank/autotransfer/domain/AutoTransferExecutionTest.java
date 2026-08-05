package com.shinhan.corebank.autotransfer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutoTransferExecutionTest {

    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime EXECUTED_AT = LocalDateTime.of(2026, 1, 31, 9, 0);

    @Nested
    @DisplayName("success()")
    class Success {

        @Test
        @DisplayName("SUCCESS 상태로 생성되고 실패사유는 없다")
        void success() {
            AutoTransferExecution e = AutoTransferExecution.success(
                    EXECUTION_DATE, 10000L, "TXN0000000000000001", EXECUTED_AT);

            assertThat(e.getExecutionDate()).isEqualTo(EXECUTION_DATE);
            assertThat(e.getAmount()).isEqualTo(10000L);
            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
            assertThat(e.getTransactionNumber()).isEqualTo("TXN0000000000000001");
            assertThat(e.getFailureReason()).isNull();
            assertThat(e.getExecutedAt()).isEqualTo(EXECUTED_AT);
        }
    }

    @Nested
    @DisplayName("error()")
    class Error {

        @Test
        @DisplayName("ERROR 상태로 생성되고 거래번호는 없다")
        void error() {
            AutoTransferExecution e = AutoTransferExecution.error(
                    EXECUTION_DATE, 10000L, "잔액 부족", EXECUTED_AT);

            assertThat(e.getExecutionDate()).isEqualTo(EXECUTION_DATE);
            assertThat(e.getAmount()).isEqualTo(10000L);
            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
            assertThat(e.getFailureReason()).isEqualTo("잔액 부족");
            assertThat(e.getTransactionNumber()).isNull();
            assertThat(e.getExecutedAt()).isEqualTo(EXECUTED_AT);
        }
    }
}
