package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerCommandUseCase;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerOperationCommand;
import com.shinhan.corebank.customer.application.port.in.AdminPasswordResetResult;
import com.shinhan.corebank.customer.application.port.in.AdminUnlockResult;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.TemporaryPasswordGeneratorPort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 고객 계정 운영 중 잠금 해제·비밀번호 초기화(#449).
 *
 * <p>감사는 Apache Fineract의 command source 방식을 따른다 — 요청 1건에 감사 1행, 행위자는 관리자, 대상은
 * {@code targetCustomerId}, 바뀐 필드만 {@code changes}에 남기고 비밀번호 값은 남기지 않는다. 실패한 요청도 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminCustomerCommandService implements AdminCustomerCommandUseCase {

    private static final String PASSWORD_CHANGED = "CHANGED";

    private final CustomerPersistencePort customerPersistencePort;
    private final TemporaryPasswordGeneratorPort temporaryPasswordGeneratorPort;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public AdminUnlockResult unlock(AdminCustomerOperationCommand command) {
        rejectSelfTarget(command, AuditEventType.ACCOUNT_UNLOCK);

        Customer customer = findTargetForUpdate(command, AuditEventType.ACCOUNT_UNLOCK);
        Map<String, Object> changes = loginStateChanges(customer);

        customer.unlockByAdmin();
        customerPersistencePort.updateLoginFailureState(customer);

        recordSuccess(command, AuditEventType.ACCOUNT_UNLOCK, changes);
        return new AdminUnlockResult(
                customer.getCustomerId(), customer.isAccountLocked(), customer.getLoginFailureCount());
    }

    @Override
    @Transactional
    public AdminPasswordResetResult resetPassword(AdminCustomerOperationCommand command) {
        rejectSelfTarget(command, AuditEventType.PASSWORD_RESET_BY_ADMIN);

        // BCrypt는 느리므로 대상 행 락을 잡기 전에 계산해 로그인 경로의 대기 시간을 늘리지 않는다.
        String temporaryPassword = temporaryPasswordGeneratorPort.generate();
        String passwordHash = passwordEncoder.encode(temporaryPassword);

        Customer customer = findTargetForUpdate(command, AuditEventType.PASSWORD_RESET_BY_ADMIN);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("password", PASSWORD_CHANGED);
        changes.putAll(loginStateChanges(customer));

        customer.resetPasswordByAdmin(passwordHash, LocalDateTime.now(clock));
        customerPersistencePort.updatePasswordResetByAdmin(customer);

        recordSuccess(command, AuditEventType.PASSWORD_RESET_BY_ADMIN, changes);
        return new AdminPasswordResetResult(
                customer.getCustomerId(),
                temporaryPassword,
                customer.isAccountLocked(),
                customer.getLoginFailureCount());
    }

    // 자기 계정 초기화는 셀프 재설정의 이메일 인증을 우회하므로 막는다.
    private void rejectSelfTarget(AdminCustomerOperationCommand command, AuditEventType eventType) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.adminCustomerId(), "adminCustomerId must not be null");
        Objects.requireNonNull(command.targetCustomerId(), "targetCustomerId must not be null");

        if (command.adminCustomerId().equals(command.targetCustomerId())) {
            recordFailure(command, eventType, "SELF_TARGET");
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 로그인 실패·성공 처리와 같은 비관적 락으로 대상 고객을 읽는다(AGENTS.md §4 TOCTOU).
    private Customer findTargetForUpdate(AdminCustomerOperationCommand command, AuditEventType eventType) {
        return customerPersistencePort
                .findByIdForUpdate(command.targetCustomerId())
                .orElseThrow(() -> {
                    recordFailure(command, eventType, "CUSTOMER_NOT_FOUND");
                    return new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND);
                });
    }

    // 해제 결과(잠금 false·실패 0) 중 지금 값과 다른 것만 남긴다.
    private Map<String, Object> loginStateChanges(Customer before) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (before.isAccountLocked()) {
            changes.put("accountLocked", false);
        }
        if (before.getLoginFailureCount() != 0) {
            changes.put("loginFailureCount", 0);
        }
        return changes;
    }

    private void recordSuccess(
            AdminCustomerOperationCommand command, AuditEventType eventType, Map<String, Object> changes) {
        auditLogService.record(
                command.adminCustomerId(),
                null,
                eventType,
                command.requestIp(),
                true,
                Map.of("targetCustomerId", command.targetCustomerId(), "changes", changes));
    }

    // 실패 기록은 AuditLogService가 별도 트랜잭션으로 커밋하므로 예외로 롤백돼도 남는다.
    private void recordFailure(AdminCustomerOperationCommand command, AuditEventType eventType, String reason) {
        auditLogService.record(
                command.adminCustomerId(),
                null,
                eventType,
                command.requestIp(),
                false,
                Map.of("targetCustomerId", command.targetCustomerId(), "reason", reason));
    }
}
