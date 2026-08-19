package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledTransferListItemResponseTest {

    private ScheduledTransferListItem item(ScheduledTransferStatus status, boolean cancelable) {
        return new ScheduledTransferListItem(
                7001L, LocalDate.of(2026, 8, 5), "110123456789", "088",
                "110987654321", "홍길동", 300_000L, status, cancelable);
    }

    @Test
    @DisplayName("계좌번호는 MaskingUtil 포맷으로, 이름은 가운데만 마스킹된다")
    void masksAccountNumbersAndName() {
        ScheduledTransferListItemResponse response = ScheduledTransferListItemResponse.from(
                item(ScheduledTransferStatus.WAITING, true));

        assertThat(response.withdrawalAccountNumber()).isEqualTo("110******789");
        assertThat(response.accountNumber()).isEqualTo("110******321");
        assertThat(response.payeeName()).isEqualTo("홍*동");
    }

    @Test
    @DisplayName("payeeBankCode 088은 신한은행으로 변환된다")
    void resolvesBankName() {
        ScheduledTransferListItemResponse response = ScheduledTransferListItemResponse.from(
                item(ScheduledTransferStatus.WAITING, true));

        assertThat(response.payeeBankName()).isEqualTo("신한은행");
    }

    @Test
    @DisplayName("cancelable은 ScheduledTransferListItem이 계산해 넘긴 값을 그대로 반영한다")
    void cancelableReflectsItemValue() {
        ScheduledTransferListItemResponse waiting = ScheduledTransferListItemResponse.from(
                item(ScheduledTransferStatus.WAITING, true));
        ScheduledTransferListItemResponse success = ScheduledTransferListItemResponse.from(
                item(ScheduledTransferStatus.SUCCESS, false));
        ScheduledTransferListItemResponse canceled = ScheduledTransferListItemResponse.from(
                item(ScheduledTransferStatus.CANCELED, false));

        assertThat(waiting.cancelable()).isTrue();
        assertThat(success.cancelable()).isFalse();
        assertThat(canceled.cancelable()).isFalse();
    }
}
