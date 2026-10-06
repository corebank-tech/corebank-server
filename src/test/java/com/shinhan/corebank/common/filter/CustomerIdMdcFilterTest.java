package com.shinhan.corebank.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CustomerIdMdcFilterTest {

    private final CustomerIdMdcFilter filter = new CustomerIdMdcFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_요청이면_마스킹된_customerId를_MDC에_싣는다() throws Exception {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(123456L, "user01", "홍길동");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                customer, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCustomerId = new String[1];
        FilterChain chain = (req, res) -> seenCustomerId[0] = MDC.get("customerId");

        filter.doFilter(request, response, chain);

        assertThat(seenCustomerId[0]).isEqualTo("****56");
    }

    @Test
    void 비인증_요청이면_customerId를_MDC에_안_싣는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCustomerId = new String[] {"바뀌지_않아야_함"};
        FilterChain chain = (req, res) -> seenCustomerId[0] = MDC.get("customerId");

        filter.doFilter(request, response, chain);

        assertThat(seenCustomerId[0]).isNull();
    }

    @Test
    void 필터가_끝나면_MDC를_정리한다() throws Exception {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(42L, "user01", "홍길동");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                customer, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("customerId")).isNull();
    }
}
