package com.shinhan.corebank.auth.application.port.out;

import com.shinhan.corebank.auth.domain.model.LoginAuditReason;

// 로그인 성공·실패 감사 기록을 저장하는 출력 Port
public interface RecordLoginAuditPort {

    void record(
            Long customerId,
            String requestIp,
            boolean success,
            LoginAuditReason reason
    );
}
