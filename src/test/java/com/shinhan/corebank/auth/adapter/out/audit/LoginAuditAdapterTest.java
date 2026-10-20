package com.shinhan.corebank.auth.adapter.out.audit;

import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.customer.api.CustomerRole;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 감사 어댑터 단위 테스트")
class LoginAuditAdapterTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LoginAuditAdapter adapter;

    // 로그인 감사 상세에는 제한된 사유와 역할만 기록
    @Test
    @DisplayName("로그인 실패를 LOGIN 이벤트와 사유·actor role 로 기록한다")
    void recordsLoginFailureWithoutSensitiveData() {
        adapter.record(1L, "192.168.0.10", false, LoginAuditReason.INVALID_CREDENTIALS, CustomerRole.CUSTOMER, null);

        verify(auditLogService)
                .record(
                        1L,
                        null,
                        AuditEventType.LOGIN,
                        "192.168.0.10",
                        false,
                        Map.of("reason", "INVALID_CREDENTIALS", "role", "CUSTOMER", "requiredRole", "ANY"));
    }

    // 로그인 성공도 공통 LOGIN 이벤트로 기록
    @Test
    @DisplayName("로그인 성공을 LOGIN 이벤트와 성공 사유로 기록한다")
    void recordsLoginSuccess() {
        adapter.record(1L, "192.168.0.10", true, LoginAuditReason.SUCCESS, CustomerRole.CUSTOMER, null);

        verify(auditLogService)
                .record(
                        1L,
                        null,
                        AuditEventType.LOGIN,
                        "192.168.0.10",
                        true,
                        Map.of("reason", "SUCCESS", "role", "CUSTOMER", "requiredRole", "ANY"));
    }

    // 계정을 못 찾은 실패에서도 관리자 경로 시도였는지 남아야 보안 모니터링에 쓸 수 있다.
    @Test
    @DisplayName("계정을 못 찾은 관리자 경로 실패는 actor role 이 UNKNOWN 이고 requiredRole 이 ADMIN 이다")
    void recordsRequiredRoleWhenCustomerNotFound() {
        adapter.record(null, "192.168.0.10", false, LoginAuditReason.INVALID_CREDENTIALS, null, CustomerRole.ADMIN);

        verify(auditLogService)
                .record(
                        null,
                        null,
                        AuditEventType.LOGIN,
                        "192.168.0.10",
                        false,
                        Map.of("reason", "INVALID_CREDENTIALS", "role", "UNKNOWN", "requiredRole", "ADMIN"));
    }
}
