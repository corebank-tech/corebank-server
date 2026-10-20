package com.shinhan.corebank.auth.adapter.out.audit;

import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.customer.api.CustomerRole;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 공통 감사 로그에 로그인 성공·실패를 기록하는 Adapter
//
// 로그아웃은 여기에 없다 — 로그아웃 감사 기록 자체가 아직 만들어지지 않았다.
@Component
@RequiredArgsConstructor
public class LoginAuditAdapter implements RecordLoginAuditPort {

    private static final String UNKNOWN_ACTOR_ROLE = "UNKNOWN";
    private static final String ANY_REQUIRED_ROLE = "ANY";

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
        String actorRole = role == null ? UNKNOWN_ACTOR_ROLE : role.name();

        // 고객 로그인은 역할을 가리지 않아 요구 역할이 없다. 관리자 경로 시도와 구분되게 ANY 로 남긴다.
        String requiredRoleName = requiredRole == null ? ANY_REQUIRED_ROLE : requiredRole.name();

        auditLogService.record(
                customerId,
                null,
                AuditEventType.LOGIN,
                requestIp,
                success,
                Map.of("reason", reason.name(), "role", actorRole, "requiredRole", requiredRoleName));
    }
}
