package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.Map;
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

    @Test
    @DisplayName("detail에 계좌번호 포함 -> 마스킹되어 저장됨")
    void detailWithAccountNumber_masked() {
        Map<String, Object> detail = Map.of("accountNumber", "110123456789");

        AuditLogJpaEntity e = AuditLogJpaEntity.of(
                1L, null, AuditEventType.LOGIN, "127.0.0.1", true, detail, NOW);

        assertThat(e.getDetail().get("accountNumber")).isEqualTo("110******789");
    }

    @Test
    @DisplayName("detail에 계좌번호·금지 키 없음 -> 원본 그대로 저장됨")
    void detailWithoutSensitiveKeys_keptAsIs() {
        Map<String, Object> detail = Map.of("eventNote", "정상 로그인");

        AuditLogJpaEntity e = AuditLogJpaEntity.of(
                1L, null, AuditEventType.LOGIN, "127.0.0.1", true, detail, NOW);

        assertThat(e.getDetail()).isEqualTo(detail);
    }

    @Test
    @DisplayName("detail에 금지 키(birthDate) 포함 -> IllegalArgumentException")
    void detailWithForbiddenKey_birthDate_throws() {
        Map<String, Object> detail = Map.of("birthDate", "19900101");

        assertThatIllegalArgumentException().isThrownBy(() ->
                AuditLogJpaEntity.of(1L, null, AuditEventType.LOGIN, "127.0.0.1", true, detail, NOW));
    }

    @Test
    @DisplayName("detail에 금지 키(otp) 포함 -> IllegalArgumentException")
    void detailWithForbiddenKey_otp_throws() {
        Map<String, Object> detail = Map.of("otp", "123456");

        assertThatIllegalArgumentException().isThrownBy(() ->
                AuditLogJpaEntity.of(1L, null, AuditEventType.LOGIN, "127.0.0.1", true, detail, NOW));
    }

    @Test
    @DisplayName("detail에 금지 키(password) 포함 -> IllegalArgumentException")
    void detailWithForbiddenKey_password_throws() {
        Map<String, Object> detail = Map.of("password", "1234");

        assertThatIllegalArgumentException().isThrownBy(() ->
                AuditLogJpaEntity.of(1L, null, AuditEventType.LOGIN, "127.0.0.1", true, detail, NOW));
    }
}