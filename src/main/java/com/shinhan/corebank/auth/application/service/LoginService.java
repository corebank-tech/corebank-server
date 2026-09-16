package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.auth.application.port.out.LoginCustomerPort;
import com.shinhan.corebank.auth.application.port.out.LoginFailureUpdateResult;
import com.shinhan.corebank.auth.application.port.out.LoginSuccessUpdateResult;
import com.shinhan.corebank.auth.application.port.out.PasswordHashVerifierPort;
import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.auth.domain.model.LoginCustomer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 고객 조회, 비밀번호 검증, 로그인 상태 저장과 감사 기록을 조율
@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    // 존재하지 않는 아이디에서도 BCrypt 비교 시간을 맞추기 위한 더미 해시 (REQ-AUTH-023, 실제 자격증명 아님)
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$TKFDPDX3R71KXVXhzHGIfuD5TTj2R8Z0uSnVgFKgO5B5p3vpqI1CG";

    private final LoginCustomerPort loginCustomerPort;
    private final PasswordHashVerifierPort passwordHashVerifierPort;
    private final LoginAttemptProcessor loginAttemptProcessor;
    private final LoginSuccessProcessor loginSuccessProcessor;
    private final RecordLoginAuditPort recordLoginAuditPort;
    private final Clock clock;

    @Override
    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        LoginCustomer loginCustomer = loginCustomerPort
                .findByUserId(command.userId())
                .orElseThrow(() -> customerNotFound(command.password(), command.requestIp()));

        if (loginCustomer.isAccountLocked()) {
            recordFailureAudit(loginCustomer.getCustomerId(), command.requestIp(), LoginAuditReason.ACCOUNT_LOCKED);
            throw LoginFailedException.accountLocked();
        }

        boolean passwordMatches = passwordHashVerifierPort.matches(command.password(), loginCustomer.getPasswordHash());

        if (!passwordMatches) {
            throw handlePasswordMismatch(loginCustomer, command.requestIp());
        }

        LoginSuccessUpdateResult updateResult = loginSuccessProcessor.process(
                loginCustomer.getCustomerId(), LocalDateTime.now(clock), command.requestIp());

        if (updateResult == LoginSuccessUpdateResult.ACCOUNT_LOCKED) {
            recordFailureAudit(loginCustomer.getCustomerId(), command.requestIp(), LoginAuditReason.ACCOUNT_LOCKED);
            throw LoginFailedException.accountLocked();
        }

        return new LoginResult(loginCustomer.getCustomerId(), loginCustomer.getUserId(), loginCustomer.getUserName());
    }

    // 존재하지 않는 아이디도 최초 실패처럼 보이는 값과 시간으로 계정 존재 여부 노출을 방지 (REQ-AUTH-023)
    private LoginFailedException customerNotFound(String rawPassword, String requestIp) {
        // BCrypt 비교를 생략하면 응답 시간 차이로 계정 존재 여부가 드러나 더미 해시로 시간을 맞춘다
        passwordHashVerifierPort.matches(rawPassword, DUMMY_PASSWORD_HASH);

        recordFailureAudit(null, requestIp, LoginAuditReason.INVALID_CREDENTIALS);

        return LoginFailedException.invalidCredentials(loginAttemptProcessor.process(1));
    }

    // 저장된 최신 실패 상태를 기준으로 오류와 노출 데이터를 결정
    private LoginFailedException handlePasswordMismatch(LoginCustomer loginCustomer, String requestIp) {
        LoginFailureUpdateResult updateResult = loginCustomerPort.recordLoginFailure(loginCustomer.getCustomerId());

        if (updateResult.accountLocked()) {
            recordFailureAudit(loginCustomer.getCustomerId(), requestIp, LoginAuditReason.ACCOUNT_LOCKED);
            return LoginFailedException.accountLocked();
        }

        LoginAttemptResult attemptResult = loginAttemptProcessor.process(updateResult.errorCount());

        recordFailureAudit(loginCustomer.getCustomerId(), requestIp, LoginAuditReason.INVALID_CREDENTIALS);

        return LoginFailedException.invalidCredentials(attemptResult);
    }

    // 감사 저장 오류가 로그인 실패 응답을 대체하지 않도록 격리
    private void recordFailureAudit(Long customerId, String requestIp, LoginAuditReason reason) {
        try {
            recordLoginAuditPort.record(customerId, requestIp, false, reason);
        } catch (RuntimeException exception) {
            log.error("로그인 실패 감사 로그 저장에 실패했습니다. customerId={}, reason={}", customerId, reason, exception);
        }
    }
}
