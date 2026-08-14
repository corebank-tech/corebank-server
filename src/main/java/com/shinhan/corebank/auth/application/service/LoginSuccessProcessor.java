package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.out.LoginCustomerPort;
import com.shinhan.corebank.auth.application.port.out.LoginSuccessUpdateResult;
import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

// 로그인 성공 상태와 성공 감사를 하나의 트랜잭션으로 저장
@Service
@RequiredArgsConstructor
public class LoginSuccessProcessor {

    private final LoginCustomerPort loginCustomerPort;
    private final RecordLoginAuditPort recordLoginAuditPort;

    @Transactional
    public LoginSuccessUpdateResult process(
            Long customerId,
            LocalDateTime loginAt,
            String requestIp
    ) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(loginAt, "loginAt must not be null");
        Objects.requireNonNull(requestIp, "requestIp must not be null");

        LoginSuccessUpdateResult updateResult =
                loginCustomerPort.recordLoginSuccess(
                        customerId,
                        loginAt,
                        requestIp
                );

        if (updateResult == LoginSuccessUpdateResult.ACCOUNT_LOCKED) {
            return updateResult;
        }

        recordLoginAuditPort.record(
                customerId,
                requestIp,
                true,
                LoginAuditReason.SUCCESS
        );

        return LoginSuccessUpdateResult.COMPLETED;
    }
}
