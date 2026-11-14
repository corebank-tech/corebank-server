package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import com.shinhan.corebank.common.filter.CorrelationIdFilter;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.AdminPermissionFacade;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({CorsProperties.class, CsrfProperties.class, AdminSessionProperties.class})
public class SecurityConfig {

    // 공개이면서 CSRF 검사까지 빼는 경로. 공개여선 안 되는 CSRF 예외는 별도 목록을 둔다.
    private static final RequestMatcher[] PUBLIC_CSRF_EXCLUDED = {
        pathPattern(HttpMethod.POST, "/auth/login"),
        pathPattern(HttpMethod.POST, "/admin/auth/login"),
        pathPattern(HttpMethod.POST, "/auth/find-id"),
        pathPattern(HttpMethod.POST, "/auth/terms/check"),
        pathPattern(HttpMethod.POST, "/auth/verify-account"),
        pathPattern(HttpMethod.POST, "/auth/check-id"),
        pathPattern(HttpMethod.POST, "/auth/signup/validate"),
        pathPattern(HttpMethod.POST, "/auth/signup/complete"),
        pathPattern(HttpMethod.POST, "/auth/email-verifications"),
        pathPattern(HttpMethod.POST, "/auth/email-verifications/{emailVerificationId}/verify"),
        pathPattern(HttpMethod.POST, "/auth/password-reset-requests"),
        pathPattern(HttpMethod.PUT, "/auth/password-reset-requests/{passwordResetRequestId}"),
        pathPattern(HttpMethod.GET, "/actuator/health"),
        // 이의제기 Agent 워커 전용(D-36). 세션이 아니라 워커 서비스 키와 작업 토큰으로 인증하고, ALB 에 공개하지 않는다.
        // /internal/** 전체를 열지 않는다. 나중에 생기는 내부 경로가 인증 없이 열리지 않게 접두어를 하나씩 적는다
        pathPattern("/internal/review-jobs/**"),
        pathPattern("/internal/objections/**"),
        pathPattern("/internal/agent-runs/**"),
        // FDS Agent는 세션 대신 전용 Bearer 토큰을 각 Controller 진입점에서 검증한다.
        pathPattern("/internal/fds/**")
    };

    // 공개하되 CSRF 보호는 유지하는 경로.
    private static final RequestMatcher[] PUBLIC_CSRF_PROTECTED = {
        pathPattern(HttpMethod.GET, "/products"),
        pathPattern(HttpMethod.GET, "/products/*"),
        pathPattern(HttpMethod.GET, "/auth/terms"),
        pathPattern(HttpMethod.GET, "/auth/signup/confirm-info")
    };

    // Swagger-UI/API 문서. "/**" 가 확장자 붙은 형제 경로를 못 잡아 진입 경로를 따로 적는다.
    private static final String[] SWAGGER_ENDPOINTS = {
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    // 로그인 비밀번호 해시 검증에 사용할 BCrypt Encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 로그인 성공 인증정보를 HttpSession에 저장
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // 로그인 시 세션 ID를 바꾸고 만료시간은 세션 기본값과 관리자 로그인 정책에 맡긴다.
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Content-Type",
                "X-XSRF-TOKEN",
                "X-Signup-Token",
                "Idempotency-Key",
                // 브라우저가 거래용 일회성 인증 토큰을 헤더로 전송할 수 있도록 preflight에서 허용한다.
                "Account-Password-Auth-Token",
                "Otp-Auth-Token",
                "X-Correlation-Id",
                // 관리자 고객 개인정보 API의 조회 사유(PH-89)
                "X-Access-Reason"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure, CsrfProperties csrfProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> {
            cookie.path("/").httpOnly(false).secure(secure).sameSite("Lax");

            // 운영 FE와 API 서브도메인이 CSRF 쿠키를 공유할 때만 Domain을 지정한다.
            if (csrfProperties.cookieDomain() != null) {
                cookie.domain(csrfProperties.cookieDomain());
            }
        });
        return repository;
    }

    @Bean
    public CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            // 요청별 보안 정책을 구성하는 Spring Security 설정 객체
            HttpSecurity http,
            SessionAuthenticationEntryPoint entryPoint,
            SessionAccessDeniedHandler deniedHandler,
            SessionLogoutSuccessHandler logoutSuccessHandler,
            CorsConfigurationSource corsConfigurationSource,
            CookieCsrfTokenRepository csrfTokenRepository,
            CsrfTokenRequestAttributeHandler csrfTokenRequestHandler,
            AdminPermissionFacade adminPermissionFacade,
            @Value("${app.security.metrics-allowed-ip:}") String metricsAllowedIp)
            throws Exception {
        http.addFilterBefore(new CorrelationIdFilter(), SecurityContextHolderFilter.class)
                // 세션 인증이 복원된 직후에 낡은 관리자 권한을 걸러 이후 인가가 인증 없는 요청으로 본다.
                .addFilterAfter(
                        new AdminPermissionVersionFilter(adminPermissionFacade), SecurityContextHolderFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // 기본 CSRF 보호를 유지하고 로그인과 회원가입, ALB 헬스체크만 검사에서 제외
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfTokenRepository);
                    csrf.csrfTokenRequestHandler(csrfTokenRequestHandler);
                    csrf.ignoringRequestMatchers(PUBLIC_CSRF_EXCLUDED);
                })
                .authorizeHttpRequests(authorize -> authorize

                        // 공개 경로. CSRF 설정과 같은 상수를 써서 두 설정이 어긋나지 않게 한다.
                        .requestMatchers(PUBLIC_CSRF_EXCLUDED)
                        .permitAll()
                        .requestMatchers(PUBLIC_CSRF_PROTECTED)
                        .permitAll()
                        .requestMatchers(SWAGGER_ENDPOINTS)
                        .permitAll()

                        // Prometheus 수집은 지정한 IP(온프레미스 VM) 한 곳에서 온 요청만 받는다. 값이 비면 아무도 못 읽는다.
                        .requestMatchers(pathPattern(HttpMethod.GET, "/actuator/prometheus"))
                        .access((authentication, context) -> new AuthorizationDecision(!metricsAllowedIp.isBlank()
                                && new IpAddressMatcher(metricsAllowedIp).matches(context.getRequest())))

                        // 약관 열람만 인증을 요구한다 — 누가 봤는지 이력을 남겨야 한다.
                        .requestMatchers(pathPattern(HttpMethod.GET, "/products/*/terms/*"))
                        .authenticated()

                        // 관리자 API는 경로별 권한으로 가린다 (PH-49a-②, admin_auth.md §6). 권한 authority 는
                        // SessionLoginManager 가 ROLE_ADMIN 로그인에만 실으므로 역할 검사를 겹치지 않는다.
                        // 먼저 걸리는 줄이 이기므로 GET 줄을 변경 줄보다 앞에 둔다. 조회 전용 경로는
                        // GET 줄만 두어 변경 요청이 맨 아래 denyAll 로 떨어지게 한다.
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/customers/**"))
                        .hasAuthority(AdminPermission.CUSTOMER_READ.name())
                        .requestMatchers(pathPattern("/admin/customers/**"))
                        .hasAuthority(AdminPermission.CUSTOMER_WRITE.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/fds/holds/**"))
                        .hasAuthority(AdminPermission.CUSTOMER_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.POST, "/admin/fds/holds/**"))
                        .hasAuthority(AdminPermission.CUSTOMER_WRITE.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/trial-balance/**"))
                        .hasAuthority(AdminPermission.GL_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/info/**"))
                        .hasAuthority(AdminPermission.GL_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/gl/integrity-check/**"))
                        .hasAuthority(AdminPermission.GL_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/ledger-reconciliation/**"))
                        .hasAuthority(AdminPermission.AUDIT_READ.name())
                        // 지급정지 사건과 이의제기(D-36). 수용, 일부 지급정지 판정의 승인과 반려만 PAYMENT_STOP_APPROVE 다(D13).
                        // 작성자와 승인자가 다른지는 paymentstop 이 직접 본다. 심사 시작 기록은 상세 화면을 여는 조회 권한으로 둔다.
                        // 승인 줄이 아래 POST /admin/objections/** 줄보다 먼저 걸려야 한다.
                        .requestMatchers(pathPattern(
                                HttpMethod.POST, "/admin/objections/{objectionId}/decisions/{decisionId}/approve"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_APPROVE.name())
                        .requestMatchers(pathPattern(
                                HttpMethod.POST, "/admin/objections/{objectionId}/decisions/{decisionId}/return"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_APPROVE.name())
                        .requestMatchers(
                                pathPattern(HttpMethod.POST, "/admin/objections/{objectionId}/review-sessions"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/payment-stops/**"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.GET, "/admin/objections/**"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_READ.name())
                        .requestMatchers(pathPattern(HttpMethod.POST, "/admin/payment-stops/**"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_WRITE.name())
                        .requestMatchers(pathPattern(HttpMethod.POST, "/admin/objections/**"))
                        .hasAuthority(AdminPermission.PAYMENT_STOP_WRITE.name())

                        // 위에 걸리지 않은 /admin 경로는 거부한다. 새 관리자 API 를 열면서 규칙을
                        // 빠뜨려도 아무 관리자에게나 열리지 않게 한다.
                        .requestMatchers(pathPattern("/admin/**"))
                        .denyAll()

                        // 인증된 사용자만 접근 가능
                        .anyRequest()
                        .authenticated())
                // Spring Security 필터 단계에서 발생한 인증/인가 예외의 응답 처리기를 연결
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                // HttpSession 생성 방식, 세션 고정 공격 방지 정책을 설정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // JDBC에서 삭제·만료된 세션은 SessionRepositoryFilter가 다음 요청부터 거부한다.
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                // CSRF 검사를 유지하고 CsrfLogoutHandler가 XSRF-TOKEN을 Path=/로 폐기한다.
                .logout(logout -> logout.logoutRequestMatcher(pathPattern(HttpMethod.POST, "/auth/logout"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
