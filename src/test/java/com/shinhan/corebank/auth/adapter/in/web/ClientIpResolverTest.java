package com.shinhan.corebank.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("X-Forwarded-For에서 ALB가 마지막에 추가한 접속 IP를 반환한다")
    void resolvesLastForwardedIpAddedByAlb() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.10, 10.0.1.20 ");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.1.20");
    }

    @Test
    @DisplayName("클라이언트가 주입한 선행 X-Forwarded-For 값을 신뢰하지 않는다")
    void ignoresClientSuppliedForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("전달 헤더가 없으면 원격 주소를 반환한다")
    void fallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(" 192.0.2.10 ");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("전달 헤더가 공백이면 원격 주소를 반환한다")
    void fallsBackWhenForwardedHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.0.2.20");

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.20");
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
        request.addHeader("X-Forwarded-For", "1".repeat(46));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 IP는 45자 이하여야 합니다.");
    }

    @Test
    @DisplayName("45자인 IP 값은 허용한다")
    void acceptsFortyFiveCharacterIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String clientIp = "1".repeat(45);
        request.addHeader("X-Forwarded-For", clientIp);

        assertThat(resolver.resolve(request)).isEqualTo(clientIp);
    }
}
