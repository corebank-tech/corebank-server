package com.shinhan.corebank.auth.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        LoginCommand command = new LoginCommand("user01", "SecretPassword1!", "192.168.0.10");

        assertThat(command.toString()).doesNotContain("SecretPassword1!").contains("password=[PROTECTED]");
    }

    // 빈 문자열과 공백만 있는 요청 IP는 저장 경계에서 거부
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("요청 IP가 비어 있으면 생성할 수 없다")
    void rejectsBlankRequestIp(String requestIp) {
        assertThatThrownBy(() -> new LoginCommand("user01", "SecretPassword1!", requestIp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestIp must not be blank");
    }

    // DB 컬럼 길이를 초과하는 요청 IP는 저장 전에 거부
    @Test
    @DisplayName("요청 IP가 45자를 초과하면 생성할 수 없다")
    void rejectsRequestIpLongerThanFortyFiveCharacters() {
        assertThatThrownBy(() -> new LoginCommand("user01", "SecretPassword1!", "a".repeat(46)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestIp must not exceed 45 characters");
    }

    // IPv6 최대 문자열 길이인 45자는 허용
    @Test
    @DisplayName("요청 IP가 45자이면 생성할 수 있다")
    void acceptsRequestIpWithFortyFiveCharacters() {
        LoginCommand command = new LoginCommand("user01", "SecretPassword1!", "a".repeat(45));

        assertThat(command.requestIp()).hasSize(45);
    }
}
