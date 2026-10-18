package com.shinhan.corebank.auth.application.port.out;

import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.customer.api.CustomerRole;

// 로그인 성공·실패 감사 기록을 저장하는 출력 Port
public interface RecordLoginAuditPort {

    // role 은 로그인을 시도한 계정의 구분이다. 아이디가 없어 계정을 못 찾았으면 null 이다.
    void record(Long customerId, String requestIp, boolean success, LoginAuditReason reason, CustomerRole role);
}
