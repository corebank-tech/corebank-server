package com.shinhan.corebank.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTransferMessageTest {

    @Test
    @DisplayName("성공한 이체는 상대방과 천 단위로 구분한 금액을 담는다")
    void succeeded_withCounterparty_showsNameAndGroupedAmount() {
        // when
        NotificationTransferMessage message =
                NotificationTransferMessage.succeeded(NotificationType.TRANSFER, 1_000_000L, "홍*동");

        // then
        assertThat(message.title()).isEqualTo("이체 완료");
        assertThat(message.content()).isEqualTo("홍*동님에게 1,000,000원을 보냈습니다.");
    }

    @Test
    @DisplayName("상대방 이름이 없으면 금액만으로 문장을 만든다 — 타행은 성공 응답 전에는 이름을 모른다")
    void succeeded_withoutCounterparty_omitsName() {
        // when
        NotificationTransferMessage message =
                NotificationTransferMessage.succeeded(NotificationType.TRANSFER, 5_000L, null);

        // then
        assertThat(message.content()).isEqualTo("5,000원을 보냈습니다.");
    }

    @Test
    @DisplayName("고객이 조치할 수 있는 실패 사유는 문구로 바꿔 본문 끝에 붙인다")
    void failed_withMappedErrorCode_appendsCustomerReason() {
        // when
        NotificationTransferMessage message =
                NotificationTransferMessage.failed(NotificationType.TRANSFER, 30_000L, "홍*동", "TRF0303");

        // then
        assertThat(message.title()).isEqualTo("이체 실패");
        assertThat(message.content()).isEqualTo("홍*동님에게 30,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("매핑하지 않은 코드 · 내부 오류 · 코드 없음은 기본 문장만 쓰고 코드를 드러내지 않는다")
    void failed_withUnmappedOrMissingErrorCode_showsDefaultSentenceOnly() {
        // when
        NotificationTransferMessage unmapped =
                NotificationTransferMessage.failed(NotificationType.TRANSFER, 30_000L, null, "TRF0002");
        NotificationTransferMessage internal =
                NotificationTransferMessage.failed(NotificationType.TRANSFER, 30_000L, null, "CMN9999");
        NotificationTransferMessage missing =
                NotificationTransferMessage.failed(NotificationType.TRANSFER, 30_000L, null, null);

        // then
        assertThat(unmapped.content()).isEqualTo("30,000원을 보내지 못했습니다.");
        assertThat(internal.content()).isEqualTo("30,000원을 보내지 못했습니다.");
        assertThat(missing.content()).isEqualTo("30,000원을 보내지 못했습니다.");
    }

    @Test
    @DisplayName("예약 · 자동이체는 제목 앞에 이체 종류를 붙이고 본문은 즉시이체와 같은 규칙을 쓴다")
    void titleFollowsNotificationType_contentKeepsTransferRule() {
        // when
        NotificationTransferMessage scheduled =
                NotificationTransferMessage.succeeded(NotificationType.SCHEDULED_TRANSFER, 10_000L, "홍*동");
        NotificationTransferMessage auto =
                NotificationTransferMessage.failed(NotificationType.AUTO_TRANSFER, 10_000L, "홍*동", "TRF0303");

        // then
        assertThat(scheduled.title()).isEqualTo("예약이체 완료");
        assertThat(scheduled.content()).isEqualTo("홍*동님에게 10,000원을 보냈습니다.");
        assertThat(auto.title()).isEqualTo("자동이체 실패");
        assertThat(auto.content()).isEqualTo("홍*동님에게 10,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }
}
