package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import org.springframework.stereotype.Component;

// 잠기기 전 로그인 실패 횟수로 남은 시도 횟수를 계산
@Component
public class LoginAttemptProcessor {

    private static final int MAX_LOGIN_FAILURE_COUNT = 5;

    // 저장 완료된 실패 횟수를 로그인 시도 결과로 변환
    public LoginAttemptResult process(int persistedErrorCount) {
        validateErrorCount(persistedErrorCount);

        return new LoginAttemptResult(persistedErrorCount, MAX_LOGIN_FAILURE_COUNT - persistedErrorCount);
    }

    // 실패 횟수 데이터가 노출되는 1회 이상 4회 이하만 허용
    private void validateErrorCount(int persistedErrorCount) {
        if (persistedErrorCount < 1 || persistedErrorCount >= MAX_LOGIN_FAILURE_COUNT) {
            throw new IllegalArgumentException("로그인 실패 횟수는 1회 이상 4회 이하여야 합니다.");
        }
    }
}
