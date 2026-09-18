package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class AdminBootstrapAuthorizationManagerTest {

    private static final RequestAuthorizationContext CONTEXT =
            new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/admin/customers"));

    @Test
    @DisplayName("허용 목록에 있는 고객은 통과한다")
    void grantsCustomerInAllowlist() {
        assertThat(isGranted(List.of(7L), customer(7L))).isTrue();
    }

    @Test
    @DisplayName("허용 목록 밖의 고객은 거부한다")
    void deniesCustomerOutsideAllowlist() {
        assertThat(isGranted(List.of(7L), customer(8L))).isFalse();
    }

    @Test
    @DisplayName("허용 목록이 비어 있으면 누구도 통과하지 못한다")
    void deniesEveryoneWhenAllowlistIsEmpty() {
        assertThat(isGranted(List.of(), customer(7L))).isFalse();
    }

    @Test
    @DisplayName("익명 사용자는 isAuthenticated()가 true여도 거부한다")
    void deniesAnonymous() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(isGranted(List.of(7L), anonymous)).isFalse();
    }

    @Test
    @DisplayName("principal이 AuthenticatedCustomer가 아니면 거부한다")
    void deniesUnexpectedPrincipalType() {
        Authentication other = UsernamePasswordAuthenticationToken.authenticated(
                "7", null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));

        assertThat(isGranted(List.of(7L), other)).isFalse();
    }

    private boolean isGranted(List<Long> allowlist, Authentication authentication) {
        AdminBootstrapAuthorizationManager manager =
                new AdminBootstrapAuthorizationManager(new AdminBootstrapProperties(allowlist));
        return manager.authorize(() -> authentication, CONTEXT).isGranted();
    }

    private Authentication customer(Long customerId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, "user" + customerId, "테스터"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
