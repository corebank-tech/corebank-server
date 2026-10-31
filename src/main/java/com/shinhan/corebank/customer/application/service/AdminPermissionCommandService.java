package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.CustomerRole;
import com.shinhan.corebank.customer.application.port.in.AdminPermissionCommand;
import com.shinhan.corebank.customer.application.port.in.AdminPermissionCommandUseCase;
import com.shinhan.corebank.customer.application.port.out.AdminPermissionPort;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 권한 부여·회수(PH-49b).
 *
 * <p>권한 변경은 개인정보 조회·변경이 아니라 조회 사유 없이 일반 감사 이벤트로 남긴다. 요청 1건에 감사 1행, 행위자는
 * 관리자, 대상은 {@code targetCustomerId}, 바뀐 것만 {@code changes}에 남기고 실패한 요청도 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionCommandService implements AdminPermissionCommandUseCase {

    private final CustomerPersistencePort customerPersistencePort;
    private final AdminPermissionPort adminPermissionPort;
    private final CustomerSessionInvalidator customerSessionInvalidator;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public List<AdminPermission> grantPermission(AdminPermissionCommand command, AdminPermission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        rejectSelfTarget(command, AuditEventType.ADMIN_PERMISSION_GRANT);

        // 같은 대상의 부여·회수를 대상 행 락으로 직렬화하고, 중복은 PK 가 마지막에 막는다.
        Customer customer = findTargetForUpdate(command, AuditEventType.ADMIN_PERMISSION_GRANT);
        rejectNonAdminTarget(customer, command, AuditEventType.ADMIN_PERMISSION_GRANT);

        boolean granted = adminPermissionPort.grant(customer.getCustomerId(), permission, LocalDateTime.now(clock));

        recordSuccess(command, AuditEventType.ADMIN_PERMISSION_GRANT, permissionChanges(permission, granted, true));
        return adminPermissionPort.findPermissions(customer.getCustomerId());
    }

    @Override
    @Transactional
    public List<AdminPermission> revokePermission(AdminPermissionCommand command, AdminPermission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        rejectSelfTarget(command, AuditEventType.ADMIN_PERMISSION_REVOKE);

        Customer customer = findTargetForUpdate(command, AuditEventType.ADMIN_PERMISSION_REVOKE);
        rejectNonAdminTarget(customer, command, AuditEventType.ADMIN_PERMISSION_REVOKE);

        boolean revoked = adminPermissionPort.revoke(customer.getCustomerId(), permission);
        if (revoked) {
            // 권한은 로그인 때 세션에 실려 행만 지우면 이미 로그인한 세션에 남으므로 같은 트랜잭션에서 끊는다.
            customerSessionInvalidator.invalidateAll(customer.getCustomerId());
        }

        recordSuccess(command, AuditEventType.ADMIN_PERMISSION_REVOKE, permissionChanges(permission, revoked, false));
        return adminPermissionPort.findPermissions(customer.getCustomerId());
    }

    private void rejectSelfTarget(AdminPermissionCommand command, AuditEventType eventType) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.adminCustomerId(), "adminCustomerId must not be null");
        Objects.requireNonNull(command.targetCustomerId(), "targetCustomerId must not be null");

        if (command.adminCustomerId().equals(command.targetCustomerId())) {
            recordFailure(command, eventType, "SELF_TARGET");
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 권한은 관리자에게만 붙는다. DB 복합 FK 가 막기 전에 400 으로 돌려준다.
    private void rejectNonAdminTarget(Customer target, AdminPermissionCommand command, AuditEventType eventType) {
        if (target.getRole() != CustomerRole.ADMIN) {
            recordFailure(command, eventType, "TARGET_NOT_ADMIN");
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 비관적 락(SELECT ... FOR UPDATE)으로 대상 고객을 읽는다(AGENTS.md §4 TOCTOU).
    private Customer findTargetForUpdate(AdminPermissionCommand command, AuditEventType eventType) {
        return customerPersistencePort
                .findByIdForUpdate(command.targetCustomerId())
                .orElseThrow(() -> {
                    recordFailure(command, eventType, "CUSTOMER_NOT_FOUND");
                    return new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND);
                });
    }

    // 실제로 행이 바뀐 경우에만 무엇이 바뀌었는지 남긴다.
    private Map<String, Object> permissionChanges(AdminPermission permission, boolean changed, boolean granted) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (changed) {
            changes.put("permission", permission.name());
            changes.put("granted", granted);
        }
        return changes;
    }

    private void recordSuccess(AdminPermissionCommand command, AuditEventType eventType, Map<String, Object> changes) {
        auditLogService.record(
                command.adminCustomerId(),
                null,
                eventType,
                command.requestIp(),
                true,
                Map.of("targetCustomerId", command.targetCustomerId(), "changes", changes));
    }

    // 실패 기록은 AuditLogService가 별도 트랜잭션으로 커밋하므로 예외로 롤백돼도 남는다.
    private void recordFailure(AdminPermissionCommand command, AuditEventType eventType, String reason) {
        auditLogService.record(
                command.adminCustomerId(),
                null,
                eventType,
                command.requestIp(),
                false,
                Map.of("targetCustomerId", command.targetCustomerId(), "reason", reason));
    }
}
