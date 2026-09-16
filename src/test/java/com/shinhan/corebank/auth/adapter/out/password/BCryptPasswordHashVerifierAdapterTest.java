package com.shinhan.corebank.auth.adapter.out.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("BCrypt 비밀번호 해시 검증 어댑터 단위 테스트")
class BCryptPasswordHashVerifierAdapterTest {

    private static final String RAW_PASSWORD = "CorrectPassword1!";

    private PasswordEncoder passwordEncoder;
    private BCryptPasswordHashVerifierAdapter adapter;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        adapter = new BCryptPasswordHashVerifierAdapter(passwordEncoder);
    }

    // 평문 비교가 아니라 BCrypt 해시 검증을 사용
    @Test
    @DisplayName("올바른 비밀번호는 BCrypt 해시와 일치한다")
    void matchesCorrectPassword() {
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(RAW_PASSWORD).isNotEqualTo(passwordHash);
        assertThat(adapter.matches(RAW_PASSWORD, passwordHash)).isTrue();
    }

    // 다른 비밀번호는 저장된 BCrypt 해시와 일치하지 않음
    @Test
    @DisplayName("다른 비밀번호는 BCrypt 해시와 일치하지 않는다")
    void rejectsIncorrectPassword() {
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(adapter.matches("WrongPassword1!", passwordHash)).isFalse();
    }
}
