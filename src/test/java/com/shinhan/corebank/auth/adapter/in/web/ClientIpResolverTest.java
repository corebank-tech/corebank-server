package com.shinhan.corebank.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("X-Forwarded-For 헤더를 읽지 않고 원격 주소를 반환한다")
    void ignoresForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.10");
        request.setRemoteAddr("192.0.2.30");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.30");
    }

    @Test
    @DisplayName("원격 주소의 앞뒤 공백을 제거해 반환한다")
    void trimsRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(" 192.0.2.10 ");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("추출된 IP가 공백이면 거부한다")
    void rejectsBlankIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("   ");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 IP는 필수입니다.");
    }

    @Test
    @DisplayName("추출된 IP가 45자를 초과하면 거부한다")
    void rejectsIpLongerThanFortyFiveCharacters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1".repeat(46));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 IP는 45자 이하여야 합니다.");
    }

    @Test
    @DisplayName("45자인 IP 값은 허용한다")
    void acceptsFortyFiveCharacterIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String clientIp = "1".repeat(45);
        request.setRemoteAddr(clientIp);

        assertThat(resolver.resolve(request)).isEqualTo(clientIp);
    }
}
