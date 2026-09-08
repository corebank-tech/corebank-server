package com.shinhan.corebank.auth.application.port.out;

import java.util.Set;

// Auth가 Account 모듈에 계좌 소유권과 비밀번호 검증을 요청하는 포트를 정의한다.
public interface FindIdAccountVerificationPort {

    FindIdAccountVerificationResult verify(
            Set<Long> candidateCustomerIds,
            String accountNumber,
            String accountPassword
    );
}
