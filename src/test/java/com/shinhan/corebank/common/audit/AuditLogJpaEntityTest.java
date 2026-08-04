package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogJpaEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    @DisplayName("원장 변경 이벤트 + 거래번호 있음 -> 정상 생성")
    void ledgerChanging_withTransactionNumber_created() {
        AuditLogJpaEntity e = AuditLogJpaEntity.of(
                1L, "TXN00000000000000001", AuditEventType.IMMEDIATE_TRANSFER,
                "127.0.0.1", true, null, NOW);

        assertThat(e.getTransactionNumber()).isEqualTo("TXN00000000000000001");
    }

    @Test
    @DisplayName("원장 변경 이벤트 + 거래번호 누락 -> IllegalArgumentException")
    void ledgerChanging_withoutTransactionNumber_throws() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AuditLogJpaEntity.of(1L, null, AuditEventType.IMMEDIATE_TRANSFER,
                        "127.0.0.1", true, null, NOW));
    }

    @Test
    @DisplayName("원장 변경 이벤트 + 거래번호 공백 -> IllegalArgumentException")
    void ledgerChanging_withBlankTransactionNumber_throws() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AuditLogJpaEntity.of(1L, "  ", AuditEventType.IMMEDIATE_TRANSFER,
                        "127.0.0.1", true, null, NOW));
    }

    @Test
    @DisplayName("원장 비변경 이벤트 + 거래번호 없음 -> 정상 생성")
    void nonLedgerChanging_withoutTransactionNumber_created() {
        AuditLogJpaEntity e = AuditLogJpaEntity.of(
                1L, null, AuditEventType.LOGIN, "127.0.0.1", true, null, NOW);

        assertThat(e.getTransactionNumber()).isNull();
    }

    @Test
    @DisplayName("원장 비변경 이벤트 + 거래번호 오기입 -> IllegalArgumentException")
    void nonLedgerChanging_withTransactionNumber_throws() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                AuditLogJpaEntity.of(1L, "TXN00000000000000001", AuditEventType.LOGIN,
                        "127.0.0.1", true, null, NOW));
    }
}