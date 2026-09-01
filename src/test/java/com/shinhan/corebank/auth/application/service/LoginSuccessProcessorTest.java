package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.application.port.out.LoginCustomerPort;
import com.shinhan.corebank.auth.application.port.out.LoginSuccessUpdateResult;
import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 성공 처리기 단위 테스트")
class LoginSuccessProcessorTest {

    private static final LocalDateTime LOGIN_AT = LocalDateTime.of(2026, 8, 15, 1, 30);
    private static final String REQUEST_IP = "192.168.0.10";

    @Mock
    private LoginCustomerPort loginCustomerPort;

    @Mock
    private RecordLoginAuditPort recordLoginAuditPort;

    @InjectMocks
    private LoginSuccessProcessor processor;

    // 로그인 상태 저장 완료 후 같은 트랜잭션에서 성공 감사를 기록
    @Test
    @DisplayName("로그인 성공 상태와 성공 감사를 함께 저장한다")
    void recordsSuccessfulLoginAndAudit() {
        given(loginCustomerPort.recordLoginSuccess(1L, LOGIN_AT, REQUEST_IP))
                .willReturn(LoginSuccessUpdateResult.COMPLETED);

        LoginSuccessUpdateResult result = processor.process(1L, LOGIN_AT, REQUEST_IP);

        assertThat(result).isEqualTo(LoginSuccessUpdateResult.COMPLETED);
        verify(recordLoginAuditPort).record(1L, REQUEST_IP, true, LoginAuditReason.SUCCESS);
    }

    // 동시 잠금 결과에는 성공 감사를 기록하지 않음
    @Test
    @DisplayName("계정이 잠겼으면 성공 감사를 저장하지 않는다")
    void skipsSuccessAuditForLockedAccount() {
        given(loginCustomerPort.recordLoginSuccess(1L, LOGIN_AT, REQUEST_IP))
                .willReturn(LoginSuccessUpdateResult.ACCOUNT_LOCKED);

        LoginSuccessUpdateResult result = processor.process(1L, LOGIN_AT, REQUEST_IP);

        assertThat(result).isEqualTo(LoginSuccessUpdateResult.ACCOUNT_LOCKED);
        verify(recordLoginAuditPort, never()).record(1L, REQUEST_IP, true, LoginAuditReason.SUCCESS);
    }

    // 성공 감사 저장 오류를 호출자에게 전파해 트랜잭션 롤백을 유도
    @Test
    @DisplayName("성공 감사 저장에 실패하면 예외를 전파한다")
    void propagatesSuccessAuditFailure() {
        given(loginCustomerPort.recordLoginSuccess(1L, LOGIN_AT, REQUEST_IP))
                .willReturn(LoginSuccessUpdateResult.COMPLETED);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(recordLoginAuditPort)
                .record(1L, REQUEST_IP, true, LoginAuditReason.SUCCESS);

        assertThatThrownBy(() -> processor.process(1L, LOGIN_AT, REQUEST_IP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }
}
