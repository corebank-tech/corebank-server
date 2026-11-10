package com.shinhan.corebank.auth.application.port.out;

// 가입되지 않은 아이디의 로그인 실패 횟수를 저장·조회하는 출력 포트. 아이디는 호출부가 소문자로 정규화해 넘긴다.
public interface UnregisteredLoginFailurePort {

    // 만료 전 누적 실패 횟수. 기록이 없거나 만료됐으면 0
    int findActiveFailureCount(String normalizedUserId);

    // 실패를 1 더하고 더한 뒤 횟수를 반환한다. 만료된 기록이면 1부터 다시 센다
    int recordFailure(String normalizedUserId);

    // 만료된 기록을 최대 limit 건 지우고 지운 건수를 반환한다
    int deleteExpired(int limit);
}
