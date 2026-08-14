package com.shinhan.corebank.auth.domain.exception;

import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import com.shinhan.corebank.common.exception.BusinessException;

import java.util.Objects;
import java.util.Optional;

// 로그인 실패 유형에 따라 시도 횟수의 노출 여부를 제한하는 예외
public final class LoginFailedException extends BusinessException {

    private final LoginAttemptResult attemptResult;

    private LoginFailedException(
            AuthErrorCode errorCode,
            LoginAttemptResult attemptResult
    ) {
        super(errorCode);
        this.attemptResult = attemptResult;
    }

    // 존재하지 않는 아이디의 로그인 실패를 생성
    public static LoginFailedException customerNotFound() {
        return new LoginFailedException(
                AuthErrorCode.LOGIN_FAILED,
                null
        );
    }

    // 잠금 전 비밀번호 불일치 결과를 생성
    public static LoginFailedException invalidCredentials(
            LoginAttemptResult attemptResult
    ) {
        validateAttemptResult(attemptResult);

        return new LoginFailedException(
                AuthErrorCode.LOGIN_FAILED,
                attemptResult
        );
    }

    // 실패 5회 도달 또는 이미 잠긴 계정 오류를 생성
    public static LoginFailedException accountLocked() {
        return new LoginFailedException(
                AuthErrorCode.ACCOUNT_LOCKED,
                null
        );
    }

    // 실패 응답에 포함할 로그인 시도 결과를 반환
    public Optional<LoginAttemptResult> getAttemptResult() {
        return Optional.ofNullable(attemptResult);
    }

    // 비밀번호 불일치 결과가 반드시 존재하는지 확인
    private static void validateAttemptResult(
            LoginAttemptResult attemptResult
    ) {
        Objects.requireNonNull(
                attemptResult,
                "attemptResult must not be null"
        );
    }
}