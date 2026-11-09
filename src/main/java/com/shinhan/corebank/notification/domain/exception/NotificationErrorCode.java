package com.shinhan.corebank.notification.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 알림 도메인 오류코드. 값은 docs/api_conventions.md §4-16 마스터를 따른다. */
@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND("NTF0201", 404, "알림을 찾을 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;
}
