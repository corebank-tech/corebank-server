package com.shinhan.corebank.auth.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.customer.api.CustomerRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("로그인 명령 단위 테스트")
class LoginCommandTest {

    // 문자열 표현에서 평문 비밀번호를 제거
    @Test
    @DisplayName("toString은 비밀번호를 노출하지 않는다")
    void protectsPasswordInToString() {
        LoginCommand command = new LoginCommand("user01", "SecretPassword1!", "192.168.0.10", CustomerRole.CUSTOMER);

        assertThat(command.toString()).doesNotContain("SecretPassword1!").contains("password=[PROTECTED]");
    }

    // 빈 문자열과 공백만 있는 요청 IP는 저장 경계에서 거부
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("요청 IP가 비어 있으면 생성할 수 없다")
    void rejectsBlankRequestIp(String requestIp) {
        assertThatThrownBy(() -> new LoginCommand("user01", "SecretPassword1!", requestIp, CustomerRole.CUSTOMER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestIp must not be blank");
    }

    // DB 컬럼 길이를 초과하는 요청 IP는 저장 전에 거부
    @Test
    @DisplayName("요청 IP가 45자를 초과하면 생성할 수 없다")
    void rejectsRequestIpLongerThanFortyFiveCharacters() {
        assertThatThrownBy(() -> new LoginCommand("user01", "SecretPassword1!", "a".repeat(46), CustomerRole.CUSTOMER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestIp must not exceed 45 characters");
    }

    // 역할을 가리지 않는 경로를 두면 관리자가 고객 문으로 들어와 경로 분리가 무의미해진다.
    // 새 로그인 경로를 만들면서 요구 역할을 빠뜨리는 일을 여기서 막는다.
    @Test
    @DisplayName("요구 역할이 없으면 생성할 수 없다")
    void rejectsMissingRequiredRole() {
        assertThatThrownBy(() -> new LoginCommand("user01", "SecretPassword1!", "192.168.0.10", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("requiredRole must not be null");
    }

    // IPv6 최대 문자열 길이인 45자는 허용
    @Test
    @DisplayName("요청 IP가 45자이면 생성할 수 있다")
    void acceptsRequestIpWithFortyFiveCharacters() {
        LoginCommand command = new LoginCommand("user01", "SecretPassword1!", "a".repeat(45), CustomerRole.CUSTOMER);

        assertThat(command.requestIp()).hasSize(45);
    }
}
