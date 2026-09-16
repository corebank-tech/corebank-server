package com.shinhan.corebank.auth.adapter.out.password;

import com.shinhan.corebank.auth.application.port.out.PasswordHashVerifierPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Spring Security BCrypt 구현으로 비밀번호 해시를 검증하는 Adapter
@Component
@RequiredArgsConstructor
public class BCryptPasswordHashVerifierAdapter implements PasswordHashVerifierPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
