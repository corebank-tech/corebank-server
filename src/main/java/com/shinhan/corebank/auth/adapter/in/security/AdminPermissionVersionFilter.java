package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermissionFacade;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 권한 회수 뒤에 남은 관리자 세션을 거부한다.
 *
 * <p>회수는 저장된 세션을 지우지만, 회수보다 먼저 권한을 읽은 로그인이 그 뒤에 세션을 저장하면 지우지 못한다.
 * 그래서 로그인 때 받은 권한 버전을 요청마다 DB 값과 맞춰 보고, 다르면 인증 없는 요청으로 돌려 401 이 나게 한다.
 */
@RequiredArgsConstructor
class AdminPermissionVersionFilter extends OncePerRequestFilter {

    private final AdminPermissionFacade adminPermissionFacade;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (hasOutdatedPermissions(authentication)) {
            discardAuthentication(request);
        }

        filterChain.doFilter(request, response);
    }

    // 고객 세션과 이 필터 도입 전에 만든 관리자 세션에는 버전이 없어 비교하지 않는다.
    private boolean hasOutdatedPermissions(Authentication authentication) {
        if (authentication == null
                || !(authentication.getDetails() instanceof AdminPermissionStamp stamp)
                || !(authentication.getPrincipal() instanceof AuthenticatedCustomer admin)) {
            return false;
        }

        return adminPermissionFacade.findPermissionVersion(admin.customerId()) != stamp.version();
    }

    private void discardAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();

        final HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }
}
