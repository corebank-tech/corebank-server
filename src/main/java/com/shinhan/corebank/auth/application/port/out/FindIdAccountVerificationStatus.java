package com.shinhan.corebank.auth.application.port.out;

// 아이디 찾기에서 사용하는 계좌 본인확인 결과 상태를 정의한다.
public enum FindIdAccountVerificationStatus {
    VERIFIED,
    INFORMATION_MISMATCH,
    PASSWORD_MISMATCH,
    LOCKED
}
