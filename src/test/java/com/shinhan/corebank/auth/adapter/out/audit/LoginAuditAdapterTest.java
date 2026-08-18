package com.shinhan.corebank.auth.adapter.out.audit;

import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 감사 어댑터 단위 테스트")
class LoginAuditAdapterTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LoginAuditAdapter adapter;

    // 로그인 감사 상세에는 제한된 사유만 기록
    @Test
    @DisplayName("로그인 실패를 LOGIN 이벤트와 사유만으로 기록한다")
    void recordsLoginFailureWithoutSensitiveData() {
        adapter.record(
                1L,
                "192.168.0.10",
                false,
                LoginAuditReason.INVALID_CREDENTIALS
        );

        verify(auditLogService).record(
                1L,
                null,
                AuditEventType.LOGIN,
                "192.168.0.10",
                false,
                Map.of("reason", "INVALID_CREDENTIALS")
        );
    }

    // 로그인 성공도 공통 LOGIN 이벤트로 기록
    @Test
    @DisplayName("로그인 성공을 LOGIN 이벤트와 성공 사유로 기록한다")
    void recordsLoginSuccess() {
        adapter.record(
                1L,
                "192.168.0.10",
                true,
                LoginAuditReason.SUCCESS
        );

        verify(auditLogService).record(
                1L,
                null,
                AuditEventType.LOGIN,
                "192.168.0.10",
                true,
                Map.of("reason", "SUCCESS")
        );
    }
}
