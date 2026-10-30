package com.shinhan.corebank.gl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrialBalance 도메인 단위 테스트")
class TrialBalanceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private static TrialBalanceLine registered(String code, long debit, long credit, long count) {
        return new TrialBalanceLine(
                code, "예수금", GlAccountClass.LIABILITY, GlNormalBalance.CREDIT, debit, credit, count);
    }

    @Test
    @DisplayName("계정별 차변·대변을 더한 총계가 같으면 일치이고 차액은 0")
    void balancedWhenGrandTotalsMatch() {
        TrialBalance result = new TrialBalance(
                FROM, TO, List.of(registered("10100", 5_000L, 0L, 1), registered("20100", 3_000L, 8_000L, 3)));

        assertThat(result.debitGrandTotal()).isEqualTo(8_000L);
        assertThat(result.creditGrandTotal()).isEqualTo(8_000L);
        assertThat(result.difference()).isZero();
        assertThat(result.balanced()).isTrue();
        assertThat(result.journalEntryCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("총계가 다르면 불일치이고 차액은 차변 총계 - 대변 총계")
    void unbalancedWhenGrandTotalsDiffer() {
        TrialBalance result = new TrialBalance(FROM, TO, List.of(registered("20100", 10_000L, 7_000L, 2)));

        assertThat(result.difference()).isEqualTo(3_000L);
        assertThat(result.balanced()).isFalse();
    }

    @Test
    @DisplayName("미등록 계정 행도 총계에 들어간다 — 빼면 차대변이 맞는 것처럼 보인다")
    void unregisteredAccountCountsTowardTotals() {
        TrialBalanceLine unregistered = new TrialBalanceLine("19999", null, null, null, 4_000L, 0L, 1);

        TrialBalance result = new TrialBalance(FROM, TO, List.of(registered("20100", 0L, 4_000L, 1), unregistered));

        assertThat(result.rows()).hasSize(2);
        assertThat(result.debitGrandTotal()).isEqualTo(4_000L);
        assertThat(result.balanced()).isTrue();
    }

    @Test
    @DisplayName("분개가 없는 기간은 행 없이 총계 0, 일치")
    void emptyPeriodIsBalanced() {
        TrialBalance result = new TrialBalance(FROM, TO, List.of());

        assertThat(result.rows()).isEmpty();
        assertThat(result.debitGrandTotal()).isZero();
        assertThat(result.creditGrandTotal()).isZero();
        assertThat(result.balanced()).isTrue();
        assertThat(result.journalEntryCount()).isZero();
    }
}
