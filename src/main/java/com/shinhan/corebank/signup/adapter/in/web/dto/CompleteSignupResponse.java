package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CompleteSignupResult;
import java.time.OffsetDateTime;

// 회원가입 완료 결과를 HTTP 응답으로 반환한다.
public record CompleteSignupResponse(
        Long customerId,
        String userId,
        OffsetDateTime joinedAt
) {
    public static CompleteSignupResponse from(CompleteSignupResult result) {
        return new CompleteSignupResponse(
                result.customerId(), result.userId(), result.joinedAt()
        );
    }
}
