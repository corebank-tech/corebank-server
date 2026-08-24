package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledTransferListItemResponseTest {

    private ScheduledTransferListItem item(ScheduledTransferStatus status, boolean cancelable) {
        return new ScheduledTransferListItem(
                7001L, 101L, LocalDate.of(2026, 8, 5), "110123456789", "우리집", "088",
                "110987654321", "홍길동", 300_000L, "생활비", status, cancelable,
                LocalDateTime.of(2026, 8, 1, 10, 0));
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

    @Test
    @DisplayName("fromAlias·myPassbookMemo·registeredAt은 마스킹 없이 그대로 전달된다")
    void passesThroughAliasMemoAndRegisteredAt() {
        ScheduledTransferListItemResponse response = ScheduledTransferListItemResponse.from(
                item(ScheduledTransferStatus.WAITING, true));

        assertThat(response.fromAlias()).isEqualTo("우리집");
        assertThat(response.myPassbookMemo()).isEqualTo("생활비");
        assertThat(response.registeredAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    @Test
    @DisplayName("fromAlias가 없으면(미설정 계좌) null이 그대로 내려간다")
    void fromAliasNull_whenNotSet() {
        ScheduledTransferListItem item = new ScheduledTransferListItem(
                7001L, 101L, LocalDate.of(2026, 8, 5), "110123456789", null, "088",
                "110987654321", "홍길동", 300_000L, "생활비", ScheduledTransferStatus.WAITING, true,
                LocalDateTime.of(2026, 8, 1, 10, 0));

        ScheduledTransferListItemResponse response = ScheduledTransferListItemResponse.from(item);

        assertThat(response.fromAlias()).isNull();
    }

    @Test
    @DisplayName("출금계좌 ID가 그대로 전달된다")
    void passesThroughWithdrawalAccountId() {
        ScheduledTransferListItemResponse response =
                ScheduledTransferListItemResponse.from(
                        item(ScheduledTransferStatus.WAITING, true)
                );

        assertThat(response.withdrawalAccountId()).isEqualTo(101L);
    }
}
