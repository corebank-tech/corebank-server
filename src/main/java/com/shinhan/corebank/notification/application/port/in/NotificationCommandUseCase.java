package com.shinhan.corebank.notification.application.port.in;

import com.shinhan.corebank.notification.application.port.in.dto.NotificationTransferResultCommand;

/** 업무 결과를 고객 알림으로 남긴다. 호출자는 notification 안의 이벤트 구독자뿐이다. */
public interface NotificationCommandUseCase {

    void createTransferResultNotification(NotificationTransferResultCommand command);
}
