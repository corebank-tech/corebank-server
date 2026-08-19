package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdCommand;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdResult;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserIdCheckServiceTest {

    private static final Duration USER_ID_TTL = Duration.ofMinutes(3);
    private static final String TOKEN = "USER_ID_CHECK_test-token";

    @Mock SignupCustomerAvailabilityPort customerAvailabilityPort;
    @Mock UserIdCheckTokenPort tokenPort;
    @Mock AuthTokenGeneratorPort tokenGeneratorPort;

    UserIdCheckService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-19T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new UserIdCheckService(
                customerAvailabilityPort,
                tokenPort,
                tokenGeneratorPort,
                new SignupTokenProperties(
                        Duration.ofMinutes(30),
                        USER_ID_TTL,
                        Duration.ofMinutes(30)
                ),
                clock
        );
    }

    @Test
    @DisplayName("사용 가능한 아이디면 180초 유효 토큰을 발급한다")
    void issuesTokenForAvailableUserId() {
        given(customerAvailabilityPort.isUserIdTaken("user1234"))
                .willReturn(false);
        given(tokenGeneratorPort.generateUserIdCheckToken())
                .willReturn(TOKEN);

        CheckUserIdResult result = service.check(
                new CheckUserIdCommand("user1234")
        );

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.userIdCheckToken()).isEqualTo(TOKEN);
        assertThat(result.expiresIn()).isEqualTo(180L);

        ArgumentCaptor<UserIdCheckTokenPayload> payload =
                ArgumentCaptor.forClass(UserIdCheckTokenPayload.class);
        verify(tokenPort).save(
                org.mockito.ArgumentMatchers.eq(TOKEN),
                payload.capture(),
                org.mockito.ArgumentMatchers.eq(USER_ID_TTL)
        );
        assertThat(payload.getValue().userId()).isEqualTo("user1234");
    }

    @Test
    @DisplayName("아이디 형식이 잘못되면 ATH0004이고 토큰을 만들지 않는다")
    void rejectsInvalidFormats() {
        for (String userId : new String[]{
                "User123", "abc12", "abcdefghijklmnopq", "user_123", "한글아이디"
        }) {
            BusinessException exception = catchThrowableOfType(
                    () -> service.check(new CheckUserIdCommand(userId)),
                    BusinessException.class
            );
            assertThat(exception.getErrorCode())
                    .isEqualTo(SignupErrorCode.INVALID_USER_ID_FORMAT);
        }

        verify(tokenGeneratorPort, never()).generateUserIdCheckToken();
        verify(tokenPort, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("이미 사용 중인 아이디면 ATH0301이고 토큰을 만들지 않는다")
    void rejectsDuplicateUserId() {
        given(customerAvailabilityPort.isUserIdTaken("user1234"))
                .willReturn(true);

        BusinessException exception = catchThrowableOfType(
                () -> service.check(new CheckUserIdCommand("user1234")),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SignupErrorCode.DUPLICATE_USER_ID);
        verify(tokenGeneratorPort, never()).generateUserIdCheckToken();
        verify(tokenPort, never()).save(any(), any(), any());
    }
}
