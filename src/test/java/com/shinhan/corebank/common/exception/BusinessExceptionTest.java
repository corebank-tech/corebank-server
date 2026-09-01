package com.shinhan.corebank.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    @DisplayName("ErrorCode 만 전달 -> ErrorCode 의 메시지를 그대로 사용")
    void errorCodeOnly_usesErrorCodeMessage() {
        BusinessException e = new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_DATE_RANGE);
        assertThat(e.getMessage()).isEqualTo("조회 시작일이 종료일보다 늦습니다.");
    }

    @Test
    @DisplayName("ErrorCode + 커스텀 메시지 -> 커스텀 메시지가 우선")
    void customMessage_overridesErrorCodeMessage() {
        BusinessException e = new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING, "accountNumber 가 없습니다.");

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        assertThat(e.getMessage()).isEqualTo("accountNumber 가 없습니다.");
    }

    @Test
    @DisplayName("ErrorCode + cause -> cause 가 보존되고 메시지는 ErrorCode 의 것을 사용")
    void withCause_preservesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException e = new BusinessException(CommonErrorCode.INTERNAL_ERROR, cause);

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Test
    @DisplayName("CommonErrorCode 는 code/status/message 를 정의된 값 그대로 반환한다")
    void commonErrorCode_exposesFixedValues() {
        assertThat(CommonErrorCode.INVALID_INPUT.getCode()).isEqualTo("CMN0001");
        assertThat(CommonErrorCode.INVALID_INPUT.getStatus()).isEqualTo(400);

        assertThat(CommonErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(401);
        assertThat(CommonErrorCode.FORBIDDEN.getStatus()).isEqualTo(403);
        assertThat(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS.getStatus()).isEqualTo(409);
        assertThat(CommonErrorCode.INTERNAL_ERROR.getStatus()).isEqualTo(500);
    }
}
