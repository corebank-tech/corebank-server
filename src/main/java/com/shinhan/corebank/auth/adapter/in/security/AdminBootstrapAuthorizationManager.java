package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * S0 임시 가드(#448). 로그인한 고객 PK가 허용 목록에 있을 때만 /admin/** 접근을 허용한다.
 * PH-49a 역할 기반 인가가 들어오면 이 클래스와 {@link AdminBootstrapProperties}를 함께 지운다.
 *
 * <p>@Component로 두지 않는다 — WebMvcTest 슬라이스가 AuthorizationManager를 스캔하지 않아
 * SecurityConfig를 import하는 테스트가 전부 깨진다. SecurityConfig에서 직접 생성한다.
 */
class AdminBootstrapAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapAuthorizationManager.class);

    private final List<Long> allowedCustomerIds;

    AdminBootstrapAuthorizationManager(AdminBootstrapProperties properties) {
        this.allowedCustomerIds = properties.bootstrapCustomerIds();
    }

    // null을 반환하면 AuthorizationFilter가 허용으로 처리하므로 항상 결정을 반환한다.
    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        // 익명 토큰도 isAuthenticated()가 true라서 principal 타입으로 로그인 여부를 판단한다.
        if (!(authentication.get().getPrincipal() instanceof AuthenticatedCustomer customer)) {
            return new AuthorizationDecision(false);
        }

        boolean allowed = allowedCustomerIds.contains(customer.customerId());
        if (!allowed) {
            log.warn(
                    "관리자 허용 목록 밖 접근 거부: customerId={}, uri={}",
                    customer.customerId(),
                    context.getRequest().getRequestURI());
        }
        return new AuthorizationDecision(allowed);
    }
}
