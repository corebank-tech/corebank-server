package com.shinhan.corebank.autotransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferListItemResponseTest {

    private AutoTransfer autoTransfer() {
        return AutoTransfer.register(1L, 1L, "110987654321", "홍길동", 100_000L, 1, 15,
                LocalDate.now().plusDays(1), LocalDate.now().plusMonths(6), "내메모", "받는메모", LocalDateTime.now());
    }

    @Test
    @DisplayName("fromAlias는 AutoTransferListItem에 담긴 값을 그대로 반영한다")
    void reflectsFromAlias() {
        AutoTransferListItemResponse response = AutoTransferListItemResponse.from(
                new AutoTransferListItem(autoTransfer(), "월세계좌"));

        assertThat(response.fromAlias()).isEqualTo("월세계좌");
    }

    @Test
    @DisplayName("별칭이 없으면 fromAlias는 null로 전달된다")
    void fromAliasIsNullWhenNotSet() {
        AutoTransferListItemResponse response = AutoTransferListItemResponse.from(
                new AutoTransferListItem(autoTransfer(), null));

        assertThat(response.fromAlias()).isNull();
    }

    @Test
    @DisplayName("registeredAt은 AutoTransfer 도메인의 값을 그대로 노출한다")
    void reflectsRegisteredAt() {
        AutoTransfer autoTransfer = autoTransfer();

        AutoTransferListItemResponse response = AutoTransferListItemResponse.from(
                new AutoTransferListItem(autoTransfer, null));

        assertThat(response.registeredAt()).isEqualTo(autoTransfer.getRegisteredAt());
    }
}
