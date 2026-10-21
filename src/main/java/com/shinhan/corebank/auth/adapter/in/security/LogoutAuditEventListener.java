package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.adapter.in.web.ClientIpResolver;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.application.port.in.RecordLogoutAuditUseCase;
import com.shinhan.corebank.customer.api.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로그아웃 감사를 남긴다 (tasks.md PH-49a-② 「관리자 로그인·로그아웃 감사로그」).
 *
 * <p>Spring Security 가 {@code LogoutConfigurer} 에서 {@code LogoutSuccessEventPublishingLogoutHandler}
 * 를 조건 없이 등록하므로 로그아웃 성공마다 이 이벤트가 온다. 그래서 SessionLogoutSuccessHandler 를
 * 고치지 않는다 — 그 핸들러에 의존성을 넣으면 그것을 import 하는 WebMvcTest 슬라이스 13개가 함께 깨진다.
 */
@Component
@RequiredArgsConstructor
public class LogoutAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(LogoutAuditEventListener.class);

    // SessionLoginManager 가 "ROLE_" + role.name() 으로 싣는다. 같은 enum 을 기준으로 적어 역할 이름이
    // 바뀌면 함께 바뀌게 한다 — 어긋나도 예외가 없어 로그아웃 감사가 조용히 CUSTOMER 로만 남는다.
    private static final String ADMIN_AUTHORITY = "ROLE_" + CustomerRole.ADMIN.name();

    private final RecordLogoutAuditUseCase recordLogoutAuditUseCase;
    private final ClientIpResolver clientIpResolver;

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        Authentication authentication = event.getAuthentication();

        if (!(authentication.getPrincipal() instanceof AuthenticatedCustomer customer)) {
            return;
        }

        // 이벤트가 오는 시점에 SecurityContextLogoutHandler 가 이미 세션을 끊었다. 여기서 감사 저장이
        // 실패해 예외가 나가면 실제로 로그아웃된 사용자가 500 을 받는다 — 응답을 대체하지 않게 격리한다
        // (LoginService.recordFailureAudit 과 같은 판단).
        try {
            recordLogoutAuditUseCase.recordLogout(customer.customerId(), resolveIp(), roleOf(authentication));
        } catch (RuntimeException exception) {
            log.error("로그아웃 감사 로그 저장에 실패했습니다. customerId={}", customer.customerId(), exception);
        }
    }

    // 역할은 세션 authority 에서 읽는다 — 로그인 때 실어 둔 값이라 DB 를 다시 보지 않는다.
    private CustomerRole roleOf(Authentication authentication) {
        boolean isAdmin = AuthorityUtils.authorityListToSet(authentication.getAuthorities())
                .contains(ADMIN_AUTHORITY);

        return isAdmin ? CustomerRole.ADMIN : CustomerRole.CUSTOMER;
    }

    // 이벤트는 Authentication 만 들고 오고 audit_log.request_ip 는 NOT NULL 이라 요청을 따로 꺼낸다.
    private String resolveIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        return clientIpResolver.resolve(request);
    }
}
