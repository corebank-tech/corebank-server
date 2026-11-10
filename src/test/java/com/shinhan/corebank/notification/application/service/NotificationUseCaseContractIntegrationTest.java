package com.shinhan.corebank.notification.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.notification.application.port.in.NotificationMarkReadUseCase;
import com.shinhan.corebank.notification.application.port.in.NotificationQueryUseCase;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationUseCaseContractIntegrationTest extends IntegrationTestSupport {

    @Autowired
    NotificationQueryUseCase notificationQueryUseCase;

    @Autowired
    NotificationMarkReadUseCase notificationMarkReadUseCase;

    @Test
    @DisplayName("컨트롤러를 거치지 않고 customerId 없이 목록을 조회하면 계약 위반이다")
    void getNotifications_nullCustomerId_violatesContract() {
        assertThatThrownBy(() -> notificationQueryUseCase.getNotifications(null, 0, 10, false))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("컨트롤러를 거치지 않고 customerId 없이 미읽음 건수를 조회하면 계약 위반이다")
    void getUnreadCount_nullCustomerId_violatesContract() {
        assertThatThrownBy(() -> notificationQueryUseCase.getUnreadCount(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("컨트롤러를 거치지 않고 customerId 없이 읽음 처리하면 계약 위반이다")
    void markAsRead_nullCustomerId_violatesContract() {
        assertThatThrownBy(() -> notificationMarkReadUseCase.markAsRead(null, 1L))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("컨트롤러를 거치지 않고 notificationId 없이 읽음 처리하면 계약 위반이다")
    void markAsRead_nullNotificationId_violatesContract() {
        assertThatThrownBy(() -> notificationMarkReadUseCase.markAsRead(1L, null))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
