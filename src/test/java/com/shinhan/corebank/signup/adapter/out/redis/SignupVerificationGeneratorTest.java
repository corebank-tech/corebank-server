package com.shinhan.corebank.signup.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupVerificationGeneratorTest {

    @Test
    @DisplayName("이메일 인증 요청 ID는 EVF 접두어와 256비트 난수로 생성한다")
    void generatesSecureVerificationRequestId() {
        String id = new SecureVerificationRequestIdGenerator().generateEmailVerificationId();

        assertThat(id).startsWith("EVF_");
        assertThat(Base64.getUrlDecoder().decode(id.substring(4))).hasSize(32);
    }

    @Test
    @DisplayName("이메일 인증번호는 앞자리 0을 포함할 수 있는 6자리 숫자다")
    void generatesSixDigitCode() {
        SecureEmailVerificationCodeGenerator generator = new SecureEmailVerificationCodeGenerator();

        for (int index = 0; index < 100; index++) {
            assertThat(generator.generate()).matches("\\d{6}");
        }
    }
}
