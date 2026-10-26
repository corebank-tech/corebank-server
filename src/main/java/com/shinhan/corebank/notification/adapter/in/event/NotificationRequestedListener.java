package com.shinhan.corebank.notification.adapter.in.event;

import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationRequestCarrier;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 업무 이벤트가 실어 보낸 고객 알림을 저장한다(#706, PR #723 리뷰). 업무 이벤트는 NotificationRequestCarrier 를 구현하기만
 * 하면 이 리스너 하나로 들어온다 — notification 은 업무의 종류도 문구도 모른다. 업무가 커밋된 뒤 별도 스레드 · 새
 * 트랜잭션에서 돌고, 실패하면 발행 기록(EVENT_PUBLICATION)이 남아 다시 보낼 수 있다(ADR-0010).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequestedListener {

    private final NotificationCommandUseCase notificationCommandUseCase;

    @ApplicationModuleListener
    public void on(NotificationRequestCarrier event) {
        event.notification().ifPresent(this::create);
    }

    private void create(NotificationRequest request) {
        // 타행 확정 경로는 출금계좌 조회가 비면 수신 고객 없이 발행한다. 다시 보내도 수신 고객을 알 수 없어 재제출
        // 대상으로 남기지 않는다. 알림 한 건이 사라지는 데이터 결함이라 모니터링(PH-47)이 보는 ERROR 로 남긴다
        if (request.customerId() == null) {
            log.error("수신 고객이 없어 알림 유실 - type={}, refId={}", request.type(), request.refId());
            return;
        }
        notificationCommandUseCase.createNotification(request);
    }
}
