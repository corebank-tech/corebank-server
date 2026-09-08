package com.shinhan.corebank.auth.application.port.out;

// 성명과 생년월일이 일치하는 아이디 찾기 후보 고객을 표현한다.
public record FindIdCustomerCandidate(
        Long customerId,
        String userId
) {
}
