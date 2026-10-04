package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Phase2BulkSeedSpecTest {

    @Test
    @DisplayName("운영 PH-60b 규격은 합의한 신규분 건수를 반환한다")
    void returnsProductionCounts() {
        Phase2BulkSeedSpec spec = Phase2BulkSeedSpec.production();

        assertThat(spec.transactionCount()).isEqualTo(3_000_000);
        assertThat(spec.ledgerEntryCount()).isEqualTo(6_000_000);
        assertThat(spec.voucherCount()).isEqualTo(3_000_000);
        assertThat(spec.journalEntryCount()).isEqualTo(6_000_000);
    }

    @Test
    @DisplayName("고객당 계좌 세 개 규칙을 검증한다")
    void rejectsInvalidAccountCount() {
        assertThatThrownBy(() -> spec(0, 0, 2, 0, 0, 0, 2, 1, validStart(), validEnd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("고객당 3개");
    }

    @Test
    @DisplayName("거래와 가입 구성 규칙을 검증한다")
    void rejectsInvalidTransactionComposition() {
        assertThatThrownBy(() -> spec(2, 6, 2, 3, 0, 0, 2, 1, validStart(), validEnd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거래 구성");
    }

    @Test
    @DisplayName("단기예금 상태 건수 범위를 검증한다")
    void rejectsInvalidMaturityCounts() {
        assertThatThrownBy(() -> spec(2, 6, 4, 4, 2, 1, 2, 1, validStart(), validEnd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("단기예금 상태");
    }

    @Test
    @DisplayName("휴면 후보 건수 범위를 검증한다")
    void rejectsInvalidDormantCount() {
        assertThatThrownBy(() -> spec(2, 6, 4, 4, 0, 0, 2, 3, validStart(), validEnd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("휴면 후보");
    }

    @Test
    @DisplayName("자동이체와 예약이체 건수 범위를 검증한다")
    void rejectsInvalidRecurringTransferCounts() {
        assertThatThrownBy(() -> spec(2, 6, 4, 4, 0, 0, 3, 1, validStart(), validEnd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동·예약이체");
    }

    @Test
    @DisplayName("트랜잭션 크기는 JDBC 배치 크기의 배수여야 한다")
    void rejectsInvalidChunkSize() {
        assertThatThrownBy(() -> spec(2, 6, 4, 4, 0, 0, 2, 1, validStart(), validEnd(), 3, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC 배치");
    }

    @Test
    @DisplayName("거래 종료일은 시작일보다 빠를 수 없다")
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> spec(2, 6, 4, 4, 0, 0, 2, 1, validEnd(), validStart()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거래 기간");
    }

    private Phase2BulkSeedSpec spec(
            int customers,
            int accounts,
            int transfers,
            int subscriptions,
            int matured,
            int nearMaturity,
            int recurring,
            int dormant,
            LocalDate start,
            LocalDate end) {
        return spec(
                customers,
                accounts,
                transfers,
                subscriptions,
                matured,
                nearMaturity,
                recurring,
                dormant,
                start,
                end,
                2,
                1);
    }

    private Phase2BulkSeedSpec spec(
            int customers,
            int accounts,
            int transfers,
            int subscriptions,
            int matured,
            int nearMaturity,
            int recurring,
            int dormant,
            LocalDate start,
            LocalDate end,
            int chunk,
            int batch) {
        return new Phase2BulkSeedSpec(
                customers,
                accounts,
                transfers,
                subscriptions,
                recurring,
                recurring,
                matured,
                nearMaturity,
                dormant,
                chunk,
                batch,
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                start,
                end);
    }

    private LocalDate validStart() {
        return LocalDate.of(2026, 7, 1);
    }

    private LocalDate validEnd() {
        return LocalDate.of(2026, 9, 30);
    }
}
