package com.shinhan.corebank.scheduledtransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledTransferNotificationComposerTest {

    private static final Long CUSTOMER_ID = 7071L;
    private static final Long SCHEDULED_TRANSFER_ID = 7_071_001L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 27, 9, 0);

    @Test
    @DisplayName("성공한 예약이체는 예약 건 ID 로 마스킹한 수취인과 금액의 예약이체 완료 알림을 만든다")
    void succeeded_composesScheduledTransferNotification() {
        // when
        NotificationRequest notification = ScheduledTransferNotificationComposer.compose(
                scheduledTransfer("홍길동"), ProcessResultStatus.SUCCESS, Optional.empty(), OCCURRED_AT);

        // then
        assertThat(notification.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(notification.type()).isEqualTo(NotificationType.SCHEDULED_TRANSFER);
        assertThat(notification.refId()).isEqualTo(SCHEDULED_TRANSFER_ID);
        assertThat(notification.title()).isEqualTo("예약이체 완료");
        assertThat(notification.content()).isEqualTo("홍*동님에게 10,000원을 보냈습니다.");
        assertThat(notification.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("실패한 예약이체는 고객이 조치할 수 있는 사유가 있으면 본문 끝에 붙인다")
    void failedWithReason_appendsReason() {
        // when
        NotificationRequest notification = ScheduledTransferNotificationComposer.compose(
                scheduledTransfer("홍길동"), ProcessResultStatus.ERROR, Optional.of("출금계좌 잔액이 부족합니다."), OCCURRED_AT);

        // then
        assertThat(notification.title()).isEqualTo("예약이체 실패");
        assertThat(notification.content()).isEqualTo("홍*동님에게 10,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("사유가 없는 실패(재확정)와 수취인 이름이 없는 건은 기본 문장만 쓴다")
    void failedWithoutReasonOrPayeeName_writesDefaultSentence() {
        // when
        NotificationRequest notification = ScheduledTransferNotificationComposer.compose(
                scheduledTransfer(null), ProcessResultStatus.ERROR, Optional.empty(), OCCURRED_AT);

        // then
        assertThat(notification.content()).isEqualTo("10,000원을 보내지 못했습니다.");
    }

    private static ScheduledTransfer scheduledTransfer(String payeeName) {
        return ScheduledTransfer.reconstitute(
                SCHEDULED_TRANSFER_ID,
                CUSTOMER_ID,
                101L,
                "088",
                "110123456789",
                payeeName,
                10_000L,
                LocalDate.of(2026, 10, 27),
                null,
                null,
                ScheduledTransferStatus.PROCESSING,
                null,
                OCCURRED_AT.minusDays(1),
                null,
                null,
                null,
                0L);
    }
}
