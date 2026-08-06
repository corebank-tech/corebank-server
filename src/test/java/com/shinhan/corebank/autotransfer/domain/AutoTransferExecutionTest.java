package com.shinhan.corebank.autotransfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutoTransferExecutionTest {

    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime EXECUTED_AT = LocalDateTime.of(2026, 1, 31, 9, 0);

    private AutoTransferExecution processing() {
        return AutoTransferExecution.processing(EXECUTION_DATE, 10000L, EXECUTED_AT);
    }

    @Nested
    @DisplayName("processing()")
    class Processing {

        @Test
        @DisplayName("PROCESSING 상태로 생성되고 거래번호·실패사유는 없다")
        void createsProcessingExecution() {
            AutoTransferExecution e = processing();

            assertThat(e.getExecutionDate()).isEqualTo(EXECUTION_DATE);
            assertThat(e.getAmount()).isEqualTo(10000L);
            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
            assertThat(e.getTransactionNumber()).isNull();
            assertThat(e.getFailureReason()).isNull();
            assertThat(e.getExecutedAt()).isEqualTo(EXECUTED_AT);
        }
    }

    @Nested
    @DisplayName("markSuccess()")
    class MarkSuccess {

        @Test
        @DisplayName("PROCESSING 상태면 SUCCESS로 전환되고 거래번호가 저장된다")
        void success() {
            AutoTransferExecution e = processing();

            e.markSuccess("TXN0000000000000001");

            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
            assertThat(e.getTransactionNumber()).isEqualTo("TXN0000000000000001");
            assertThat(e.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("PROCESSING 상태가 아니면 예외가 발생하고 다시 전환할 수 없다")
        void rejectsWhenNotProcessing() {
            AutoTransferExecution e = processing();
            e.markSuccess("TXN0000000000000001");

            assertThatThrownBy(() -> e.markSuccess("TXN0000000000000002"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("거래번호가 null이면 거부되고 PROCESSING 상태가 유지된다")
        void rejectsNullTransactionNumber() {
            AutoTransferExecution e = processing();

            assertThatThrownBy(() -> e.markSuccess(null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
            assertThat(e.getTransactionNumber()).isNull();
        }

        @Test
        @DisplayName("거래번호가 빈 문자열이나 공백뿐이면 거부되고 PROCESSING 상태가 유지된다")
        void rejectsBlankTransactionNumber() {
            AutoTransferExecution e = processing();

            assertThatThrownBy(() -> e.markSuccess(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> e.markSuccess("   "))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
            assertThat(e.getTransactionNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("markError()")
    class MarkError {

        @Test
        @DisplayName("PROCESSING 상태면 ERROR로 전환되고 실패사유가 저장된다")
        void error() {
            AutoTransferExecution e = processing();

            e.markError("잔액 부족");

            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.ERROR);
            assertThat(e.getFailureReason()).isEqualTo("잔액 부족");
            assertThat(e.getTransactionNumber()).isNull();
        }

        @Test
        @DisplayName("이미 SUCCESS로 확정된 건은 ERROR로 전환할 수 없다")
        void rejectsWhenAlreadySucceeded() {
            AutoTransferExecution e = processing();
            e.markSuccess("TXN0000000000000001");

            assertThatThrownBy(() -> e.markError("잔액 부족"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패 사유가 null이면 거부되고 PROCESSING 상태가 유지된다")
        void rejectsNullFailureReason() {
            AutoTransferExecution e = processing();

            assertThatThrownBy(() -> e.markError(null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
            assertThat(e.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("실패 사유가 빈 문자열이나 공백뿐이면 거부되고 PROCESSING 상태가 유지된다")
        void rejectsBlankFailureReason() {
            AutoTransferExecution e = processing();

            assertThatThrownBy(() -> e.markError(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> e.markError("   "))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(e.getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
            assertThat(e.getFailureReason()).isNull();
        }
    }
}
