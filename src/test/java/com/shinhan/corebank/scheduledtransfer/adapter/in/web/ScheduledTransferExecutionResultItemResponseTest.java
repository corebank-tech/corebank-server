package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledTransferExecutionResultItemResponseTest {

    private ScheduledTransferExecutionResultItem item(ScheduledTransferStatus status, String transactionNumber, String failureReason) {
        return new ScheduledTransferExecutionResultItem(
                7001L, status, LocalDateTime.of(2026, 8, 5, 9, 0), null, "110123456789", "110987654321", "홍길동",
                300_000L, transactionNumber, failureReason);
    }

    @Test
    @DisplayName("출금계좌번호·입금계좌번호는 마스킹, 예금주명은 가운데만 마스킹된다")
    void masksAccountNumbersAndName() {
        ScheduledTransferExecutionResultItemResponse response = ScheduledTransferExecutionResultItemResponse.from(
                item(ScheduledTransferStatus.SUCCESS, "20260805BT0000000001", null));

        assertThat(response.withdrawalAccountNumber()).isEqualTo("110******789");
        assertThat(response.accountNumber()).isEqualTo("110******321");
        assertThat(response.payeeName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("거래번호·실패사유는 마스킹 없이 그대로 전달된다")
    void passesThroughTransactionNumberAndFailureReason() {
        ScheduledTransferExecutionResultItemResponse success = ScheduledTransferExecutionResultItemResponse.from(
                item(ScheduledTransferStatus.SUCCESS, "20260805BT0000000001", null));
        ScheduledTransferExecutionResultItemResponse failed = ScheduledTransferExecutionResultItemResponse.from(
                item(ScheduledTransferStatus.FAILED, null, "잔액 부족"));

        assertThat(success.transactionNumber()).isEqualTo("20260805BT0000000001");
        assertThat(success.failureReason()).isNull();
        assertThat(failed.transactionNumber()).isNull();
        assertThat(failed.failureReason()).isEqualTo("잔액 부족");
    }

    @Test
    @DisplayName("status는 변형 없이 그대로 전달된다")
    void passesThroughStatus() {
        ScheduledTransferExecutionResultItemResponse canceled = ScheduledTransferExecutionResultItemResponse.from(
                item(ScheduledTransferStatus.CANCELED, null, null));

        assertThat(canceled.status()).isEqualTo(ScheduledTransferStatus.CANCELED);
    }

    @Test
    @DisplayName("CANCELED 건은 executedAt은 null, canceledAt은 채워져서 전달된다")
    void canceledItem_hasCanceledAtNotExecutedAt() {
        ScheduledTransferExecutionResultItem canceledItem = new ScheduledTransferExecutionResultItem(
                7002L, ScheduledTransferStatus.CANCELED, null, LocalDateTime.of(2026, 8, 4, 15, 0),
                "110123456789", "110987654321", "홍길동", 300_000L, null, null);

        ScheduledTransferExecutionResultItemResponse response = ScheduledTransferExecutionResultItemResponse.from(canceledItem);

        assertThat(response.executedAt()).isNull();
        assertThat(response.canceledAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 15, 0));
    }
}
