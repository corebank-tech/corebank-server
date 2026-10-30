package com.shinhan.corebank.transfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationRequestCarrier;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 이체 한 건이 SUCCESS 또는 ERROR 로 확정됐다. 즉시·예약·자동·타행이체 모두 이 이벤트이고 txType 으로 구분한다
 * (IMMEDIATE_TRANSFER · SCHEDULED_TRANSFER · AUTO_TRANSFER · INTERBANK_TRANSFER, ledger_entry.transaction_type 과
 * 같은 값). 타행이체는 송신 전 실패·상대 은행 응답·조회거래로 결과가 확정될 때 낸다 — 처리 불명(TIMEOUT)인 동안은 내지 않는다.
 * refId 는 transfer_id. transfer 행이 생기기 전의 사전검증 실패는 발행하지 않는다 — 즉시이체는 고객이 API 응답으로
 * 이미 오류를 받는다.
 *
 * <p>고객 알림은 transfer 가 문구까지 완성해 notificationRequest 로 싣는다(PR #723 리뷰). 예약·자동이체는 발행하지 않고
 * 배치 이벤트가 알림을 싣는다.
 */
@Builder
public record TransferSettled(
        Long customerId,
        Long refId,
        String txType,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        NotificationRequest notificationRequest)
        implements DomainEvent, NotificationRequestCarrier {

    @Override
    public NotificationRequest notification() {
        return notificationRequest;
    }
}
