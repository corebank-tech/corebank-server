package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.LoginHistoryQueryPort;
import com.shinhan.corebank.customer.application.port.out.PreviousLoginRecord;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginStatusQueryServiceTest {

    @Mock
    LoginHistoryQueryPort loginHistoryQueryPort;

    @InjectMocks
    LoginStatusQueryService service;

    @Test
    @DisplayName("직전 로그인 기록이 있으면 그 일시·IP를 그대로 담아 반환한다")
    void getLoginStatus_previousLoginExists_returnsItsInfo() {
        LocalDateTime loginAt = LocalDateTime.of(2026, 3, 5, 10, 0);
        when(loginHistoryQueryPort.findPreviousSuccessfulLogin(1L))
                .thenReturn(Optional.of(new PreviousLoginRecord(loginAt, "2.2.2.2")));

        LoginStatusResult result = service.getLoginStatus(1L);

        assertThat(result.previousLoginAt()).isEqualTo(loginAt);
        assertThat(result.previousLoginIp()).isEqualTo("2.2.2.2");
    }

    @Test
    @DisplayName("직전 로그인 기록이 없으면(첫 로그인) null 값으로 채운 결과를 반환한다")
    void getLoginStatus_noPreviousLogin_returnsNullFields() {
        when(loginHistoryQueryPort.findPreviousSuccessfulLogin(2L))
                .thenReturn(Optional.empty());

        LoginStatusResult result = service.getLoginStatus(2L);

        assertThat(result.previousLoginAt()).isNull();
        assertThat(result.previousLoginIp()).isNull();
    }
}
