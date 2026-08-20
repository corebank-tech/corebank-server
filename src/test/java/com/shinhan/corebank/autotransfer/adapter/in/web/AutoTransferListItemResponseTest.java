package com.shinhan.corebank.autotransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferListItemResponseTest {

    private AutoTransferListItem item(String fromAlias) {
        return new AutoTransferListItem(
                1L, "110987654321", fromAlias, "홍길동", 100_000L,
                LocalDate.of(2026, 8, 20), LocalDate.of(2027, 2, 20), 15, 1,
                "내메모", AutoTransferStatus.NORMAL, LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    @Test
    @DisplayName("fromAlias는 AutoTransferListItem에 담긴 값을 그대로 반영한다")
    void reflectsFromAlias() {
        AutoTransferListItemResponse response = AutoTransferListItemResponse.from(item("월세계좌"));

        assertThat(response.fromAlias()).isEqualTo("월세계좌");
    }

    @Test
    @DisplayName("별칭이 없으면 fromAlias는 null로 전달된다")
    void fromAliasIsNullWhenNotSet() {
        AutoTransferListItemResponse response = AutoTransferListItemResponse.from(item(null));

        assertThat(response.fromAlias()).isNull();
    }

    @Test
    @DisplayName("registeredAt은 AutoTransferListItem의 값을 그대로 노출한다")
    void reflectsRegisteredAt() {
        AutoTransferListItemResponse response = AutoTransferListItemResponse.from(item(null));

        assertThat(response.registeredAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
    }
}
