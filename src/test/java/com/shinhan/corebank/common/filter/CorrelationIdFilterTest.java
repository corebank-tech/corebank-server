package com.shinhan.corebank.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 헤더가_없으면_UUID를_생성해서_MDC와_응답헤더에_싣는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(seenCorrelationId[0]).isNotBlank();
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seenCorrelationId[0]);
    }

    @Test
    void 헤더가_있으면_그_값을_그대로_쓴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "fixed-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(seenCorrelationId[0]).isEqualTo("fixed-id-123");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("fixed-id-123");
    }

    @Test
    void 인증된_요청이면_customerId도_MDC에_싣는다() throws Exception {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(42L, "user01", "홍길동");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                customer, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCustomerId = new String[1];
        FilterChain chain = (req, res) -> seenCustomerId[0] = MDC.get("customerId");

        filter.doFilter(request, response, chain);

        assertThat(seenCustomerId[0]).isEqualTo("42");
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
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("customerId")).isNull();
    }
}
