package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.RecordLogoutAuditUseCase;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.customer.api.CustomerRole;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그아웃 감사를 공통 감사 로그에 남긴다. 로그인 실패와 달리 되돌릴 업무가 없어 별도 트랜잭션을 쓰지 않는다.
@Service
@RequiredArgsConstructor
@Transactional
public class LogoutAuditService implements RecordLogoutAuditUseCase {

    private final AuditLogService auditLogService;

    @Override
    public void recordLogout(Long customerId, String requestIp, CustomerRole role) {
        auditLogService.record(customerId, null, AuditEventType.LOGOUT, requestIp, true, Map.of("role", role.name()));
    }
}
