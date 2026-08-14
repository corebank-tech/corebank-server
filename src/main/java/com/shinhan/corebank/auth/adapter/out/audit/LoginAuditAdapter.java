package com.shinhan.corebank.auth.adapter.out.audit;

import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

// 공통 감사 로그에 로그인 성공·실패를 기록하는 Adapter
@Component
@RequiredArgsConstructor
public class LoginAuditAdapter implements RecordLoginAuditPort {

    private final AuditLogService auditLogService;

    @Override
    public void record(
            Long customerId,
            String requestIp,
            boolean success,
            LoginAuditReason reason
    ) {
        auditLogService.record(
                customerId,
                null,
                AuditEventType.LOGIN,
                requestIp,
                success,
                Map.of("reason", reason.name())
        );
    }
}
