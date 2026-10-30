package com.shinhan.corebank.notification.application.port.in;

import com.shinhan.corebank.notification.api.NotificationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 업무 도메인이 만든 알림을 고객 알림함에 남긴다. 호출자는 notification 안의 이벤트 구독자뿐이다. */
public interface NotificationCommandUseCase {

    /**
     * 같은 업무(type + refId)의 알림이 이미 있으면 새로 만들지 않고 넘어간다 — 같은 이벤트를 두 번 받아도 한 건이다.
     * 알림이 없거나 계약을 어기면 ConstraintViolationException 을 던진다.
     */
    void createNotification(@NotNull @Valid NotificationRequest request);
}
