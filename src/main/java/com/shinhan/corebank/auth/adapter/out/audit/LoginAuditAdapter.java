package com.shinhan.corebank.auth.adapter.out.audit;

import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.customer.api.CustomerRole;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 공통 감사 로그에 로그인 성공·실패를 기록하는 Adapter. 로그아웃은 LogoutAuditService 가 맡는다.
@Component
@RequiredArgsConstructor
public class LoginAuditAdapter implements RecordLoginAuditPort {

    private final AuditLogService auditLogService;

    @Override
    public void record(
            Long customerId,
            String requestIp,
            boolean success,
            LoginAuditReason reason,
            CustomerRole role,
            CustomerRole requiredRole) {
        // Map.of 는 null 을 받지 않는다. 계정을 못 찾은 실패는 role 이 없으므로 UNKNOWN 으로 남긴다.
        String actorRole = role == null ? "UNKNOWN" : role.name();

        auditLogService.record(
                customerId,
                null,
                AuditEventType.LOGIN,
                requestIp,
                success,
                Map.of("reason", reason.name(), "role", actorRole, "requiredRole", requiredRole.name()));
    }
}
