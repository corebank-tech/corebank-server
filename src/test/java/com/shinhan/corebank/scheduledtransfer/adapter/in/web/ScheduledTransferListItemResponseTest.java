package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledTransferListItemResponseTest {

    private ScheduledTransfer scheduledTransfer(ScheduledTransferStatus status) {
        return ScheduledTransfer.reconstitute(
                7001L, 1L, 2L, "088", "110987654321", "홍길동",
                300_000L, LocalDate.of(2026, 8, 5), "내메모", "받는메모",
                status, null, LocalDateTime.now(), null, null, null);
    }

    @Test
    @DisplayName("계좌번호는 MaskingUtil 포맷으로, 이름은 가운데만 마스킹된다")
    void masksAccountNumbersAndName() {
        ScheduledTransferListItemResponse response = ScheduledTransferListItemResponse.from(
                scheduledTransfer(ScheduledTransferStatus.WAITING), "110123456789");

        assertThat(response.withdrawalAccountNumber()).isEqualTo("110******789");
        assertThat(response.payeeAccountNumber()).isEqualTo("110******321");
        assertThat(response.payeeName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("payeeBankCode 088은 신한은행으로 변환된다")
    void resolvesBankName() {
        ScheduledTransferListItemResponse response = ScheduledTransferListItemResponse.from(
                scheduledTransfer(ScheduledTransferStatus.WAITING), "110123456789");

        assertThat(response.payeeBankName()).isEqualTo("신한은행");
    }

    @Test
    @DisplayName("WAITING 상태만 cancelable이 true다")
    void cancelableOnlyWhenWaiting() {
        ScheduledTransferListItemResponse waiting = ScheduledTransferListItemResponse.from(
                scheduledTransfer(ScheduledTransferStatus.WAITING), "110123456789");
        ScheduledTransferListItemResponse success = ScheduledTransferListItemResponse.from(
                scheduledTransfer(ScheduledTransferStatus.SUCCESS), "110123456789");
        ScheduledTransferListItemResponse canceled = ScheduledTransferListItemResponse.from(
                scheduledTransfer(ScheduledTransferStatus.CANCELED), "110123456789");

        assertThat(waiting.cancelable()).isTrue();
        assertThat(success.cancelable()).isFalse();
        assertThat(canceled.cancelable()).isFalse();
    }
}
