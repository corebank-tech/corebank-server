package com.shinhan.corebank.notification.api;

import java.util.Optional;

/**
 * 고객 알림을 실어 나르는 업무 이벤트. 업무 이벤트가 이 계약을 구현하면 notification 의 리스너 하나가 받아 저장한다 —
 * 업무가 늘어도 notification 코드는 바뀌지 않는다(PR #723 리뷰).
 */
public interface NotificationRequestCarrier {

    /** 이 이벤트로 남길 알림. 알림 대상이 아닌 결과(예: 예약 · 자동이체의 이체 확정)는 비어 있다. */
    Optional<NotificationRequest> notification();
}
