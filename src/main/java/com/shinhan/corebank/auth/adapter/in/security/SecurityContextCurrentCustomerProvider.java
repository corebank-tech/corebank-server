package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentCustomerProvider implements CurrentCustomerProvider {

    // 현재 로그인 고객을 조회
    @Override
    public AuthenticatedCustomer getCurrentCustomer() {
        // SecurityContext에서 인증 객체를 가져옴
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증정보가 없거나 인증되지 않았거나 principal이 고객 타입이 아니면 401 예외 처리
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedCustomer customer)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return customer;
    }

    // 다른 모듈에서 현재 로그인 고객의 PK만 간편하게 조회
    @Override
    public Long getCurrentCustomerId() {
        // 동일한 인증 검증을 거친 고객 정보에서 customerId를 반환
        return getCurrentCustomer().customerId();
    }
}
