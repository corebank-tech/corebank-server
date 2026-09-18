package com.shinhan.corebank.customer.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 초기화용 임시 비밀번호 생성기")
class SecureTemporaryPasswordGeneratorTest {

    // SignupValidationService.PASSWORD_PATTERN 복제 — 가입 시 쓰는 로그인 비밀번호 규칙
    private static final Pattern SIGNUP_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$");
    // PR #446 PasswordResetService.PASSWORD 복제 — 셀프 재설정의 새 비밀번호 규칙
    private static final Pattern SELF_RESET_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,15}$");
    private static final Pattern AMBIGUOUS_OR_UNSAFE = Pattern.compile("[0O1lI\"'\\\\`<>&\\s]");
    private static final int SAMPLES = 1_000;

    private final SecureTemporaryPasswordGenerator generator = new SecureTemporaryPasswordGenerator();

    @Test
    @DisplayName("12자이고 가입·셀프 재설정 비밀번호 규칙을 모두 만족한다")
    void generatesPasswordSatisfyingLoginRules() {
        for (int i = 0; i < SAMPLES; i++) {
            String password = generator.generate();

            assertThat(password).hasSize(12);
            assertThat(password).matches(SIGNUP_PASSWORD_PATTERN);
            assertThat(password).matches(SELF_RESET_PASSWORD_PATTERN);
            assertThat(password).containsPattern("[A-Z]").containsPattern("[a-z]");
        }
    }

    @Test
    @DisplayName("헷갈리는 문자와 JSON·HTML에서 깨지는 문자를 쓰지 않는다")
    void excludesAmbiguousAndUnsafeCharacters() {
        for (int i = 0; i < SAMPLES; i++) {
            assertThat(AMBIGUOUS_OR_UNSAFE.matcher(generator.generate()).find()).isFalse();
        }
    }

    @Test
    @DisplayName("매번 다른 값을 만든다")
    void generatesDistinctPasswords() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            passwords.add(generator.generate());
        }

        assertThat(passwords).hasSize(SAMPLES);
    }
}
