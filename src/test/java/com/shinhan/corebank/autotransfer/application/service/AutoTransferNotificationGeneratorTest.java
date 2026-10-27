package com.shinhan.corebank.autotransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferNotificationGeneratorTest {

    private static final Long CUSTOMER_ID = 7072L;
    private static final Long EXECUTION_ID = 7_072_001L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 27, 9, 0);

    @Test
    @DisplayName("성공한 자동이체 회차는 회차 ID 로 회차 금액의 자동이체 완료 알림을 만든다")
    void succeeded_generatesAutoTransferNotification() {
        // when
        NotificationRequest notification = AutoTransferNotificationGenerator.generate(
                autoTransfer("홍길동"), execution(30_000L), ProcessResultStatus.SUCCESS, Optional.empty(), OCCURRED_AT);

        // then
        assertThat(notification.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(notification.type()).isEqualTo(NotificationType.AUTO_TRANSFER);
        assertThat(notification.refId()).isEqualTo(EXECUTION_ID);
        assertThat(notification.title()).isEqualTo("자동이체 완료");
        assertThat(notification.content()).isEqualTo("홍*동님에게 30,000원을 보냈습니다.");
        assertThat(notification.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("실패한 자동이체 회차는 고객이 조치할 수 있는 사유가 있으면 본문 끝에 붙인다")
    void failedWithReason_appendsReason() {
        // when
        NotificationRequest notification = AutoTransferNotificationGenerator.generate(
                autoTransfer("홍길동"),
                execution(30_000L),
                ProcessResultStatus.ERROR,
                Optional.of("출금계좌 잔액이 부족합니다."),
                OCCURRED_AT);

        // then
        assertThat(notification.title()).isEqualTo("자동이체 실패");
        assertThat(notification.content()).isEqualTo("홍*동님에게 30,000원을 보내지 못했습니다. 출금계좌 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("사유가 없는 실패(재확정)와 수취인 이름이 없는 건은 기본 문장만 쓴다")
    void failedWithoutReasonOrPayeeName_writesDefaultSentence() {
        // when
        NotificationRequest notification = AutoTransferNotificationGenerator.generate(
                autoTransfer(null), execution(30_000L), ProcessResultStatus.ERROR, Optional.empty(), OCCURRED_AT);

        // then
        assertThat(notification.content()).isEqualTo("30,000원을 보내지 못했습니다.");
    }

    private static AutoTransfer autoTransfer(String payeeName) {
        return AutoTransfer.reconstitute(
                1L,
                CUSTOMER_ID,
                101L,
                "110123456789",
                payeeName,
                10_000L,
                1,
                27,
                LocalDate.of(2026, 1, 27),
                LocalDate.of(2027, 1, 27),
                LocalDate.of(2026, 10, 27),
                null,
                null,
                AutoTransferStatus.NORMAL,
                OCCURRED_AT.minusMonths(10),
                null,
                null,
                0L);
    }

    private static AutoTransferExecution execution(long amount) {
        return AutoTransferExecution.reconstitute(
                EXECUTION_ID,
                LocalDate.of(2026, 10, 27),
                amount,
                ProcessResultStatus.PROCESSING,
                null,
                null,
                OCCURRED_AT);
    }
}
