package com.shinhan.corebank.notification.adapter.in.event;

import com.shinhan.corebank.notification.api.NotificationRequestCarrier;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 업무 이벤트가 실어 보낸 고객 알림을 저장한다(#706, PR #723 리뷰). 업무 이벤트는 NotificationRequestCarrier 를 구현하기만
 * 하면 이 리스너 하나로 들어온다 — notification 은 업무의 종류도 문구도 모른다. 업무가 커밋된 뒤 별도 스레드 · 새
 * 트랜잭션에서 돌고, 실패하면 발행 기록(EVENT_PUBLICATION)이 남아 다시 보낼 수 있다(ADR-0010).
 */
@Component
@RequiredArgsConstructor
public class NotificationRequestedListener {

    private final NotificationCommandUseCase notificationCommandUseCase;

    // 순간 장애만 다시 시도한다(계약 위반은 바로 실패). 동시 실패한 재시도가 DB에 한꺼번에 몰리지 않게 간격을 늘리고 흩는다
    @ApplicationModuleListener
    @Retryable(delay = 200, multiplier = 2, jitter = 100, excludes = ConstraintViolationException.class)
    public void on(NotificationRequestCarrier event) {
        notificationCommandUseCase.createNotification(event.notification());
    }
}
