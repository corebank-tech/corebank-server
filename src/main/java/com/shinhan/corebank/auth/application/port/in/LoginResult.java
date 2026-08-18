package com.shinhan.corebank.auth.application.port.in;

// 세션 인증정보 생성에 필요한 로그인 성공 결과
public record LoginResult(
        Long customerId,
        String userId,
        String userName
) {
}
