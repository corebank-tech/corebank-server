package com.shinhan.corebank.notification.application.port.in;

import com.shinhan.corebank.notification.application.port.in.dto.NotificationListResult;
import jakarta.validation.constraints.NotNull;

/** 고객 본인의 알림함을 조회한다. */
public interface NotificationQueryUseCase {

    /** 발생 시각 최신순으로 조회한다. 허용되지 않은 page·size 는 BusinessException 을 던진다. */
    NotificationListResult getNotifications(@NotNull Long customerId, int page, int size, boolean all);

    long getUnreadCount(@NotNull Long customerId);
}
