package com.shinhan.corebank.common.filter;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.util.MaskingUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Security 인증이 끝난 뒤(기본 등록 순서)에만 동작한다 - 비인증 요청엔 customerId가 없다
@Component
public class CustomerIdMdcFilter extends OncePerRequestFilter {

    private static final String CUSTOMER_ID_KEY = "customerId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedCustomer customer) {
            MDC.put(CUSTOMER_ID_KEY, MaskingUtil.maskCustomerId(customer.customerId()));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CUSTOMER_ID_KEY);
        }
    }
}
