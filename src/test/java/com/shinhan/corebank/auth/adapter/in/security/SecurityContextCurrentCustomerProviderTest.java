package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextCurrentCustomerProviderTest {

    private final SecurityContextCurrentCustomerProvider provider = new SecurityContextCurrentCustomerProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 요청에서 현재 고객과 customerId를 조회한다")
    void returnsAuthenticatedCustomer() {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(1L, "honggildong", "홍길동");
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(provider.getCurrentCustomer()).isEqualTo(customer);
        assertThat(provider.getCurrentCustomerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("인증정보가 없으면 CMN0101 예외를 발생시킨다")
    void rejectsMissingAuthentication() {
        assertUnauthorized(provider::getCurrentCustomer);
    }

    @Test
    @DisplayName("principal이 인증 고객 타입이 아니면 CMN0101 예외를 발생시킨다")
    void rejectsUnexpectedPrincipalType() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("anonymousUser", null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertUnauthorized(provider::getCurrentCustomer);
    }

    private void assertUnauthorized(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
