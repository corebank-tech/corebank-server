package com.shinhan.corebank.notification.api;

/**
 * 고객 알림을 실어 나르는 업무 이벤트. 업무 이벤트가 이 계약을 구현하면 notification 의 리스너 하나가 받아 저장한다 —
 * 업무가 늘어도 notification 코드는 바뀌지 않는다(PR #723 리뷰).
 */
public interface NotificationRequestCarrier {

    /** 이 이벤트로 남길 알림. 알림 대상이 아닌 결과는 발행하지 않는다. */
    NotificationRequest notification();
}
