package com.shinhan.corebank.customer.application.port.in;

// 관리자 비밀번호 초기화 결과. 임시 비밀번호 평문은 이 응답에만 한 번 실린다.
public record AdminPasswordResetResult(
        Long customerId, String temporaryPassword, boolean accountLocked, int loginFailureCount) {

    // 로그에 임시 비밀번호가 찍히지 않게 문자열 표현에서 뺀다.
    @Override
    public String toString() {
        return "AdminPasswordResetResult[customerId=" + customerId + ", accountLocked=" + accountLocked + "]";
    }
}
