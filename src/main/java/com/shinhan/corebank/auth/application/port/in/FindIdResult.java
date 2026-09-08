package com.shinhan.corebank.auth.application.port.in;

// 본인확인이 완료된 고객의 전체 로그인 아이디를 반환한다.
public record FindIdResult(String userId) {
}
