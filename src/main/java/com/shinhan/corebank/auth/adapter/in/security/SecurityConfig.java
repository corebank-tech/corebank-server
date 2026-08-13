package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            // 요청별 보안 정책을 구성하는 Spring Security 설정 객체
            HttpSecurity http,
            SessionAuthenticationEntryPoint entryPoint,
            SessionAccessDeniedHandler deniedHandler
    ) throws Exception {
        http
                // 기본 CSRF 보호를 유지하고 로그인과 ALB 헬스체크만 검사에서 제외
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                pathPattern(HttpMethod.POST, "/auth/login"),
                                pathPattern(HttpMethod.GET, "/actuator/health")
                        )
                )
                .authorizeHttpRequests(authorize -> authorize

                        // 로그인 없이 조회할 수 있도록 공개
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 목록/상세는 공개, 약관 열람은 이력 기록(누가 봤는지)이 필요해 인증을 요구한다.
                        // /products/**(다단계 와일드카드) 대신 세그먼트를 명시해 두 규칙이 겹치지 않게 해서,
                        // 아래 순서가 바뀌어도 약관 열람 경로가 실수로 permitAll에 흡수되지 않는다.
                        .requestMatchers(HttpMethod.GET, "/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/*/terms/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()

                        // 인증된 사용자만 접근 가능
                        .anyRequest().authenticated()
                )
                // Spring Security 필터 단계에서 발생한 인증/인가 예외의 응답 처리기를 연결
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )
                // HttpSession 생성 방식, 세션 고정 공격 방지 정책을 설정
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
