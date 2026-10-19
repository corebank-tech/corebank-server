package com.shinhan.corebank.auth.application.port.in;

import com.shinhan.corebank.customer.api.CustomerRole;

// 로그아웃 감사 기록 (PH-49a-②). 로그인과 같이 누가 어떤 역할로 끊었는지를 남긴다.
public interface RecordLogoutAuditUseCase {

    void recordLogout(Long customerId, String requestIp, CustomerRole role);
}
