package com.shinhan.corebank.common.idempotency;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 지문 구성 규칙(api_conventions.md 7-2)이 코드로 강제되는지 검증한다.
class IdempotencyFingerprintTest {

    private record LimitUpdateLike(Long oneTimeLimit, Long dailyLimit,
                                   String accountPasswordAuthToken, String otpAuthToken) {}

    @Test
    @DisplayName("*AuthToken 으로 끝나는 필드는 지문에서 빠진다")
    void excludesAuthTokenFields() {
        Map<String, Object> fingerprint = IdempotencyFingerprint.of(
                7L, new LimitUpdateLike(3_000_000L, 10_000_000L, "ACC_PWD_x", "OTP_AUTH_y"));

        assertThat(fingerprint).containsOnlyKeys("customerId", "oneTimeLimit", "dailyLimit");
        assertThat(fingerprint).containsEntry("customerId", 7L);
        assertThat(fingerprint).containsEntry("oneTimeLimit", 3_000_000L);
        assertThat(fingerprint).containsEntry("dailyLimit", 10_000_000L);
    }

    // customerId 가 빠지면 다른 고객의 응답이 재생될 수 있어 호출자 선택으로 두지 않는다.
    @Test
    @DisplayName("customerId 는 항상 들어간다")
    void alwaysIncludesCustomerId() {
        assertThat(IdempotencyFingerprint.of(7L, null)).containsExactly(Map.entry("customerId", 7L));
    }

    @Test
    @DisplayName("본문이 없으면 path variable 만으로 지문을 만든다")
    void buildsFromPathVariablesWhenRequestIsNull() {
        Map<String, Object> fingerprint =
                IdempotencyFingerprint.of(7L, null, Map.of("accountId", 1001L));

        assertThat(fingerprint).containsOnlyKeys("customerId", "accountId");
        assertThat(fingerprint).containsEntry("accountId", 1001L);
    }

    // 레코드 컴포넌트 선언 순서가 바뀌어도 같은 해시가 나와야 한다.
    private record OrderA(String alpha, String beta) {}

    private record OrderB(String beta, String alpha) {}

    @Test
    @DisplayName("필드 선언 순서가 달라도 같은 지문이 나온다")
    void isStableAcrossFieldOrder() {
        assertThat(IdempotencyFingerprint.of(7L, new OrderA("1", "2")).toString())
                .isEqualTo(IdempotencyFingerprint.of(7L, new OrderB("2", "1")).toString());
    }
}
