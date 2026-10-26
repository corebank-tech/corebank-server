package com.shinhan.corebank.customer.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CustomerPiiCryptoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(CustomerPiiCryptoConfiguration.class);

    @Test
    @DisplayName("기본값은 비활성으로, 키가 없어도 암호화 빈을 만들지 않는다")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeansOfType(CustomerPiiCrypto.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("두 개의 서로 다른 32바이트 외부 키를 주입하면 암복호화와 이메일 해시를 사용할 수 있다")
    void validKeysEnableCrypto() {
        runner.withPropertyValues("app.pii.enabled=true", aesKey((byte) 1), hmacKey((byte) 2))
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    CustomerPiiCrypto crypto = context.getBean(CustomerPiiCrypto.class);
                    String encrypted = crypto.cipher().encrypt("user@example.com", CustomerPiiCipher.Field.EMAIL);
                    assertThat(crypto.cipher().decrypt(encrypted, CustomerPiiCipher.Field.EMAIL))
                            .isEqualTo("user@example.com");
                    assertThat(crypto.emailBlindIndex().hash("User@Example.COM"))
                            .isEqualTo(crypto.emailBlindIndex().hash("user@example.com"));
                });
    }

    @Test
    @DisplayName("활성화된 상태에서 키가 누락되면 서버 설정을 실패로 처리한다")
    void missingKeysFailClosed() {
        runner.withPropertyValues("app.pii.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    @DisplayName("키의 Base64 형식이 잘못되면 서버 설정을 실패로 처리한다")
    void malformedBase64FailsClosed() {
        runner.withPropertyValues("app.pii.enabled=true", "APP_PII_AES_KEY_BASE64=INVALID!", hmacKey((byte) 2))
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    @DisplayName("복원된 키 길이가 32바이트가 아니면 서버 설정을 실패로 처리한다")
    void wrongKeyLengthFailsClosed() {
        runner.withPropertyValues(
                        "app.pii.enabled=true",
                        "APP_PII_AES_KEY_BASE64=" + Base64.getEncoder().encodeToString(new byte[16]),
                        hmacKey((byte) 2))
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    @DisplayName("암호화 키와 검색 해시 키가 같으면 서버 설정을 실패로 처리한다")
    void reusedKeyFailsClosed() {
        runner.withPropertyValues("app.pii.enabled=true", aesKey((byte) 1), hmacKey((byte) 1))
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    private static String aesKey(byte fill) {
        return "APP_PII_AES_KEY_BASE64=" + encodedTestKey(fill);
    }

    private static String hmacKey(byte fill) {
        return "APP_PII_HMAC_KEY_BASE64=" + encodedTestKey(fill);
    }

    private static String encodedTestKey(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
