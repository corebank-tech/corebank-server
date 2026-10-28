package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogRowHashTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0, 0, 123_456_000);

    @Test
    @DisplayName("같은 감사 행은 같은 64자 해시를 갖는다")
    void sameRow_sameHash() {
        AuditLogJpaEntity first = adminView(Map.of("targetCustomerId", 2L), AccessReason.AUDIT, true);
        AuditLogJpaEntity second = adminView(Map.of("targetCustomerId", 2L), AccessReason.AUDIT, true);

        assertThat(first.getRowHash()).hasSize(64).isEqualTo(second.getRowHash());
        assertThat(AuditLogRowHash.of(first)).isEqualTo(first.getRowHash());
    }

    @Test
    @DisplayName("detail 키 순서만 다르면 해시가 같다 — 중첩 맵까지")
    void detailKeyOrder_doesNotChangeHash() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("conditions", List.of("userId"));
        ordered.put("filter", linked("b", 2, "a", 1));
        Map<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("filter", linked("a", 1, "b", 2));
        reversed.put("conditions", List.of("userId"));

        assertThat(adminView(ordered, AccessReason.AUDIT, true).getRowHash())
                .isEqualTo(adminView(reversed, AccessReason.AUDIT, true).getRowHash());
    }

    @Test
    @DisplayName("detail 값 · 사유 · 결과 중 하나만 달라도 해시가 달라진다")
    void anyColumnChange_changesHash() {
        String base = adminView(Map.of("targetCustomerId", 2L), AccessReason.AUDIT, true)
                .getRowHash();

        assertThat(adminView(Map.of("targetCustomerId", 3L), AccessReason.AUDIT, true)
                        .getRowHash())
                .isNotEqualTo(base);
        assertThat(adminView(Map.of("targetCustomerId", 2L), AccessReason.INCIDENT, true)
                        .getRowHash())
                .isNotEqualTo(base);
        assertThat(adminView(Map.of("targetCustomerId", 2L), AccessReason.AUDIT, false)
                        .getRowHash())
                .isNotEqualTo(base);
    }

    private static AuditLogJpaEntity adminView(Map<String, Object> detail, AccessReason accessReason, boolean success) {
        return AuditLogJpaEntity.of(
                1L, null, AuditEventType.CUSTOMER_DETAIL_VIEW, "10.0.0.7", success, detail, accessReason, NOW);
    }

    private static Map<String, Object> linked(
            String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }
}
