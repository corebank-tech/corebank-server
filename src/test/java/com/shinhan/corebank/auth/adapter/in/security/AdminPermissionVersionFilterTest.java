package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermissionFacade;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("낡은 관리자 권한 세션 거부 필터")
class AdminPermissionVersionFilterTest {

    private static final long ADMIN_ID = 7L;

    private final AdminPermissionFacade adminPermissionFacade = mock(AdminPermissionFacade.class);
    private final FilterChain filterChain = mock(FilterChain.class);
    private final AdminPermissionVersionFilter filter = new AdminPermissionVersionFilter(adminPermissionFacade);
    private final MockHttpSession session = new MockHttpSession();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("세션의 버전이 DB보다 낡았으면 인증을 비우고 세션을 끝낸 채 다음 필터로 넘긴다")
    void discardsSessionWithOutdatedVersion() throws Exception {
        signIn(new AdminPermissionStamp(0L));
        given(adminPermissionFacade.findPermissionVersion(ADMIN_ID)).willReturn(1L);

        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(session.isInvalid()).isTrue();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("세션의 버전이 DB와 같으면 그대로 통과시킨다")
    void keepsSessionWithCurrentVersion() throws Exception {
        signIn(new AdminPermissionStamp(1L));
        given(adminPermissionFacade.findPermissionVersion(ADMIN_ID)).willReturn(1L);

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("버전이 실리지 않은 세션은 버전을 조회하지 않고 통과시킨다")
    void skipsSessionWithoutStamp() throws Exception {
        signIn(null);

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verifyNoInteractions(adminPermissionFacade);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("인증 정보가 없으면 버전을 조회하지 않고 통과시킨다")
    void skipsAnonymousRequest() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verifyNoInteractions(adminPermissionFacade);
    }

    private void signIn(AdminPermissionStamp stamp) {
        final UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(ADMIN_ID, "admin7", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        authentication.setDetails(stamp);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setSession(session);
    }
}
