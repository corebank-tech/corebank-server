package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.CustomerRole;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerCommandUseCase;
import com.shinhan.corebank.customer.application.port.in.AdminCustomerOperationCommand;
import com.shinhan.corebank.customer.application.port.in.AdminPasswordResetResult;
import com.shinhan.corebank.customer.application.port.in.AdminStatusChangeResult;
import com.shinhan.corebank.customer.application.port.in.AdminUnlockResult;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.TemporaryPasswordGeneratorPort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import com.shinhan.corebank.customer.domain.model.CustomerStatus;
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

    private final CustomerPersistencePort customerPersistencePort;
    private final TemporaryPasswordGeneratorPort temporaryPasswordGeneratorPort;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public AdminUnlockResult unlock(AdminCustomerOperationCommand command) {
        rejectSelfTarget(command, PersonalDataAccessEventType.ACCOUNT_UNLOCK);

        Customer customer = findTargetForUpdate(command, PersonalDataAccessEventType.ACCOUNT_UNLOCK);
        Map<String, Object> changes = loginStateChanges(customer);

        customer.unlockByAdmin();
        customerPersistencePort.updateLoginFailureState(customer);

        recordSuccess(command, PersonalDataAccessEventType.ACCOUNT_UNLOCK, changes);
        return new AdminUnlockResult(
                customer.getCustomerId(), customer.isAccountLocked(), customer.getLoginFailureCount());
    }

    @Override
    @Transactional
    public AdminPasswordResetResult resetPassword(AdminCustomerOperationCommand command) {
        rejectSelfTarget(command, PersonalDataAccessEventType.LOGIN_PASSWORD_RESET_BY_ADMIN);

        // BCrypt는 느리므로 대상 행 락을 잡기 전에 계산해 로그인 경로의 대기 시간을 늘리지 않는다.
        String temporaryPassword = temporaryPasswordGeneratorPort.generate();
        String passwordHash = passwordEncoder.encode(temporaryPassword);

        Customer customer = findTargetForUpdate(command, PersonalDataAccessEventType.LOGIN_PASSWORD_RESET_BY_ADMIN);
        rejectAdminTarget(customer, command, PersonalDataAccessEventType.LOGIN_PASSWORD_RESET_BY_ADMIN);

        Map<String, Object> changes = new LinkedHashMap<>();
        // "password" 키는 AuditLogJpaEntity가 금지하므로 값 없이 바뀌었다는 표시만 남긴다.
        changes.put("passwordChanged", true);
        changes.putAll(loginStateChanges(customer));

        customer.resetPasswordByAdmin(passwordHash, LocalDateTime.now(clock));
        customerPersistencePort.updatePasswordResetByAdmin(customer);

        recordSuccess(command, PersonalDataAccessEventType.LOGIN_PASSWORD_RESET_BY_ADMIN, changes);
        return new AdminPasswordResetResult(
                customer.getCustomerId(),
                temporaryPassword,
                customer.isAccountLocked(),
                customer.getLoginFailureCount());
    }

    @Override
    @Transactional
    public AdminStatusChangeResult changeStatus(AdminCustomerOperationCommand command, CustomerStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        rejectSelfTarget(command, PersonalDataAccessEventType.CUSTOMER_STATUS_CHANGE);

        // 로그인 성공 처리와 같은 락이라, 로그인 도중 정지해도 그 로그인은 성공 상태를 저장하지 못한다.
        Customer customer = findTargetForUpdate(command, PersonalDataAccessEventType.CUSTOMER_STATUS_CHANGE);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (customer.getStatus() != status) {
            changes.put("status", status.name());
        }

        customer.changeStatusByAdmin(status);
        customerPersistencePort.updateStatus(customer);

        recordSuccess(command, PersonalDataAccessEventType.CUSTOMER_STATUS_CHANGE, changes);
        return new AdminStatusChangeResult(customer.getCustomerId(), customer.getStatus());
    }

    // 자기 계정 초기화는 셀프 재설정의 이메일 인증을 우회하므로 막는다.
    private void rejectSelfTarget(AdminCustomerOperationCommand command, PersonalDataAccessEventType eventType) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.adminCustomerId(), "adminCustomerId must not be null");
        Objects.requireNonNull(command.targetCustomerId(), "targetCustomerId must not be null");

        if (command.adminCustomerId().equals(command.targetCustomerId())) {
            recordFailure(command, eventType, "SELF_TARGET");
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 관리자를 대상으로 초기화하면 임시 비밀번호를 알게 되어 그 계정을 가져갈 수 있다. 직무분리가
    // 서려면 관리자끼리는 서로의 비밀번호를 건드리지 못해야 한다(#448 제거 체크리스트).
    private void rejectAdminTarget(
            Customer target, AdminCustomerOperationCommand command, PersonalDataAccessEventType eventType) {
        if (target.getRole() == CustomerRole.ADMIN) {
            recordFailure(command, eventType, "ADMIN_TARGET");
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 로그인 실패·성공 처리와 같은 비관적 락으로 대상 고객을 읽는다(AGENTS.md §4 TOCTOU).
    private Customer findTargetForUpdate(AdminCustomerOperationCommand command, PersonalDataAccessEventType eventType) {
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
            AdminCustomerOperationCommand command, PersonalDataAccessEventType eventType, Map<String, Object> changes) {
        auditLogService.recordPersonalDataAccess(
                command.adminCustomerId(),
                eventType,
                command.requestIp(),
                true,
                Map.of("targetCustomerId", command.targetCustomerId(), "changes", changes),
                command.accessReason());
    }

    // 실패 기록은 AuditLogService가 별도 트랜잭션으로 커밋하므로 예외로 롤백돼도 남는다.
    private void recordFailure(
            AdminCustomerOperationCommand command, PersonalDataAccessEventType eventType, String reason) {
        auditLogService.recordPersonalDataAccess(
                command.adminCustomerId(),
                eventType,
                command.requestIp(),
                false,
                Map.of("targetCustomerId", command.targetCustomerId(), "reason", reason),
                command.accessReason());
    }
}
