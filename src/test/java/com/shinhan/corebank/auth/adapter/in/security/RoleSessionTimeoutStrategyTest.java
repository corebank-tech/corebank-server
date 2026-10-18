package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class RoleSessionTimeoutStrategyTest {

    @ParameterizedTest
    @CsvSource({"ROLE_CUSTOMER,600", "ROLE_ADMIN,1800", "GL_READ,600"})
    void usesAuthenticatedRoleRatherThanRequestPath(String role, int seconds) {
        var request = new MockHttpServletRequest("POST", "/admin/auth/login");
        request.getSession(true).setMaxInactiveInterval(1800);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "638001", null, List.of(new SimpleGrantedAuthority(role)));

        new RoleSessionTimeoutStrategy().onAuthentication(authentication, request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(seconds);
    }
}
