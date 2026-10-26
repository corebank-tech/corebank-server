package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationType;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferNotificationGeneratorTest {

    private static final Long CUSTOMER_ID = 7061L;
    private static final Long TRANSFER_ID = 7_061_001L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 26, 9, 30);

    @Test
    @DisplayName("성공한 즉시이체는 마스킹한 상대방과 천 단위로 구분한 금액으로 알림을 만든다")
    void succeededImmediateTransfer_generatesTransferNotification() {
        // when
        Optional<NotificationRequest> request = TransferNotificationGenerator.generate(
                transfer(TransferType.IMMEDIATE, ProcessResultStatus.SUCCESS, null, "홍길동"), CUSTOMER_ID, OCCURRED_AT);

        // then
        assertThat(request).hasValueSatisfying(notification -> {
            assertThat(notification.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(notification.type()).isEqualTo(NotificationType.TRANSFER);
            assertThat(notification.refId()).isEqualTo(TRANSFER_ID);
            assertThat(notification.title()).isEqualTo("이체 완료");
            assertThat(notification.content()).isEqualTo("홍*동님에게 1,000,000원을 보냈습니다.");
            assertThat(notification.occurredAt()).isEqualTo(OCCURRED_AT);
        });
    }

    @Test
    @DisplayName("상대방 이름이 없으면 금액만으로 문장을 만든다 — 타행은 성공 응답 전에는 이름을 모른다")
    void transferWithoutPayeeName_omitsName() {
        // when
        Optional<NotificationRequest> request = TransferNotificationGenerator.generate(
                transfer(TransferType.IMMEDIATE, ProcessResultStatus.ERROR, null, null), CUSTOMER_ID, OCCURRED_AT);

        // then
        assertThat(request).map(NotificationRequest::content).hasValue("1,000,000원을 보내지 못했습니다.");
    }

    @Test
    @DisplayName("고객이 조치할 수 있는 실패는 이체 오류코드의 문구를 본문 끝에 붙인다")
    void failedWithActionableErrorCode_appendsErrorCodeMessage() {
        // when
        Optional<NotificationRequest> request = TransferNotificationGenerator.generate(
                transfer(
                        TransferType.IMMEDIATE,
                        ProcessResultStatus.ERROR,
                        TransferErrorCode.INSUFFICIENT_BALANCE.getCode(),
                        "홍길동"),
                CUSTOMER_ID,
                OCCURRED_AT);

        // then
        assertThat(request).hasValueSatisfying(notification -> {
            assertThat(notification.title()).isEqualTo("이체 실패");
            assertThat(notification.content())
                    .isEqualTo("홍*동님에게 1,000,000원을 보내지 못했습니다. " + TransferErrorCode.INSUFFICIENT_BALANCE.getMessage());
        });
    }

    @Test
    @DisplayName("고객이 조치할 수 없는 코드 · 내부 오류 · 코드 없음은 기본 문장만 쓰고 코드를 드러내지 않는다")
    void failedWithOtherOrMissingErrorCode_showsDefaultSentenceOnly() {
        // when
        Optional<NotificationRequest> other = TransferNotificationGenerator.generate(
                transfer(TransferType.IMMEDIATE, ProcessResultStatus.ERROR, "TRF0002", null), CUSTOMER_ID, OCCURRED_AT);
        Optional<NotificationRequest> internal = TransferNotificationGenerator.generate(
                transfer(TransferType.IMMEDIATE, ProcessResultStatus.ERROR, "CMN9999", null), CUSTOMER_ID, OCCURRED_AT);

        // then
        assertThat(other).map(NotificationRequest::content).hasValue("1,000,000원을 보내지 못했습니다.");
        assertThat(internal).map(NotificationRequest::content).hasValue("1,000,000원을 보내지 못했습니다.");
    }

    @Test
    @DisplayName("예약 · 자동이체의 이체 확정은 알림을 싣지 않는다 — 그 알림은 배치가 결과마다 발행한다(#707)")
    void batchInitiatedTransfer_generatesNoNotification() {
        // when & then
        assertThat(TransferNotificationGenerator.generate(
                        transfer(TransferType.SCHEDULED, ProcessResultStatus.SUCCESS, null, "홍길동"),
                        CUSTOMER_ID,
                        OCCURRED_AT))
                .isEmpty();
        assertThat(TransferNotificationGenerator.generate(
                        transfer(TransferType.AUTO, ProcessResultStatus.SUCCESS, null, "홍길동"),
                        CUSTOMER_ID,
                        OCCURRED_AT))
                .isEmpty();
    }

    private static Transfer transfer(
            TransferType transferType, ProcessResultStatus status, String errorCode, String payeeName) {
        return Transfer.builder()
                .transferId(TRANSFER_ID)
                .transferType(transferType)
                .status(status)
                .errorCode(errorCode)
                .amount(1_000_000L)
                .payeeName(payeeName)
                .build();
    }
}
