package com.shinhan.corebank.otp.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// JSON Key 순서와 정수 Java 타입 차이가 거래내용 비교에 영향을 주지 않는지 검증한다.
class JacksonOtpTransactionDataCanonicalizerTest {

    private final JacksonOtpTransactionDataCanonicalizer canonicalizer =
            new JacksonOtpTransactionDataCanonicalizer(new ObjectMapper());

    @Test
    @DisplayName("필드 순서가 다른 JSON 객체는 같은 canonical JSON이 된다")
    void ignoresObjectFieldOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("withdrawalAccountId", 101);
        first.put("amount", 100_000);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("amount", 100_000L);
        second.put("withdrawalAccountId", 101L);

        assertThat(canonicalizer.canonicalize(first)).isEqualTo(canonicalizer.canonicalize(second));
    }

    @Test
    @DisplayName("배열 순서는 canonical JSON에서도 유지한다")
    void preservesArrayOrder() {
        String first = canonicalizer.canonicalize(Map.of("items", List.of(1, 2)));
        String second = canonicalizer.canonicalize(Map.of("items", List.of(2, 1)));

        assertThat(first).isNotEqualTo(second);
    }
}
