package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Phase2BulkSeedPlanTest {

    private static Phase2BulkSeedSpec spec;
    private static Phase2BulkSeedPlan plan;

    @BeforeAll
    static void setUp() {
        spec = Phase2BulkSeedSpec.production();
        plan = new Phase2BulkSeedPlan(spec);
    }

    @Test
    @DisplayName("TRF와 SUB를 합쳐 월별 100만 건과 급여일 20만 건을 만든다")
    void distributesMonthlyAndPaydayCounts() {
        Map<Integer, Integer> monthly = new HashMap<>();
        Map<Integer, Integer> payday = new HashMap<>();
        for (int index = 0; index < spec.transferCount(); index++) {
            LocalDate date = plan.tradeDate(index);
            monthly.merge(date.getMonthValue(), 1, Integer::sum);
            if (date.getDayOfMonth() == 25) {
                payday.merge(date.getMonthValue(), 1, Integer::sum);
            }
        }
        for (int index = 0; index < spec.subscriptionCount(); index++) {
            LocalDate date = plan.subscriptionDate(index);
            monthly.merge(date.getMonthValue(), 1, Integer::sum);
            if (date.getDayOfMonth() == 25) {
                payday.merge(date.getMonthValue(), 1, Integer::sum);
            }
        }

        assertThat(monthly)
                .containsEntry(7, 1_000_000)
                .containsEntry(8, 1_000_000)
                .containsEntry(9, 1_000_000);
        assertThat(payday).containsEntry(7, 200_000).containsEntry(8, 200_000).containsEntry(9, 200_000);
    }

    @Test
    @DisplayName("핫스팟 판정은 매 100건 중 30건이다")
    void assignsThirtyPercentToHotspots() {
        int count = 0;
        for (int index = 0; index < 1_000; index++) {
            if (plan.hotspot(index)) {
                count++;
            }
        }
        assertThat(count).isEqualTo(300);
        assertThat(plan.withdrawalAccountId(spec.customerCount()))
                .isEqualTo(spec.accountIdStart() + spec.dormantCandidateCount() * 3L);
        assertThat(plan.depositAccountId(spec.customerCount()))
                .isEqualTo(spec.accountIdStart() + (spec.dormantCandidateCount() + 1L) * 3L);
        int regular = spec.customerCount() + 30;
        assertThat(plan.withdrawalAccountId(regular))
                .isGreaterThanOrEqualTo(spec.accountIdStart() + (spec.dormantCandidateCount() + 100L) * 3L);
        assertThat(plan.depositAccountId(regular)).isNotEqualTo(plan.withdrawalAccountId(regular));
    }

    @Test
    @DisplayName("초기 자금 공급 이체는 PH-60 요구불계좌에서 신규 요구불계좌로 보낸다")
    void suppliesInitialFundsFromPhase60Accounts() {
        assertThat(plan.withdrawalAccountId(0)).isEqualTo(60_000_001L);
        assertThat(plan.withdrawalAccountId(10_000)).isEqualTo(60_000_001L);
        assertThat(plan.depositAccountId(0)).isEqualTo(spec.accountIdStart());
        assertThat(plan.transferAmount(0)).isEqualTo(12_000_000L);
        assertThat(plan.transferAmount(spec.customerCount())).isBetween(10_000L, 99_000L);
        assertThat(plan.tradeDate(0)).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("상품별 계좌번호와 고객 아이디가 계약 형식을 따른다")
    void createsContractIdentifiers() {
        assertThat(plan.accountNumber(0)).isEqualTo("088106000001");
        assertThat(plan.accountNumber(1)).isEqualTo("088236000001");
        assertThat(plan.accountNumber(2)).isEqualTo("088326000001");
        assertThat(plan.accountNumber((spec.maturedCount() + spec.nearMaturityCount()) * 3 + 1))
                .isEqualTo("088206000001");
        assertThat(plan.customerUserId(0)).isEqualTo("ph60b00001").matches("^[a-z][a-z0-9]{5,15}$");
    }

    @Test
    @DisplayName("MATURED와 만기 임박 단기예금 규칙을 구분한다")
    void createsMaturityScenarios() {
        assertThat(plan.productCode(0)).isEqualTo("PRD_SHORT_DEP");
        assertThat(plan.termMonths(0)).isEqualTo(1);
        assertThat(plan.accountStatus(0)).isEqualTo("MATURED");
        assertThat(plan.maturityDate(0)).isEqualTo(LocalDate.of(2026, 8, 15));

        int near = spec.maturedCount();
        assertThat(plan.termMonths(near)).isEqualTo(3);
        assertThat(plan.accountStatus(near)).isEqualTo("ACTIVE");
        assertThat(plan.maturityDate(near)).isEqualTo(LocalDate.of(2026, 10, 24));
    }

    @Test
    @DisplayName("일반 정기예금과 적금은 상품 기간과 금액 단위를 따른다")
    void createsRegularSubscriptions() {
        int deposit = spec.maturedCount() + spec.nearMaturityCount();
        int savings = spec.customerCount();

        assertThat(plan.productCode(deposit)).isEqualTo("PRD_BASIC_DEP");
        assertThat(plan.subscriptionDate(deposit)).isAfterOrEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(plan.subscriptionAmount(deposit)).isEqualTo(1_000_000L);
        assertThat(plan.subscriptionAccountId(deposit)).isEqualTo(plan.timeDepositAccountId(deposit));

        assertThat(plan.productCode(savings)).isEqualTo("PRD_REGULAR_SAVE");
        assertThat(plan.subscriptionDate(savings)).isAfterOrEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(plan.subscriptionAmount(savings)).isEqualTo(50_000L);
        assertThat(plan.subscriptionAccountId(savings)).isEqualTo(plan.savingsAccountId(0));
    }

    @Test
    @DisplayName("거래번호는 거래일과 채널 및 10자리 일련번호를 포함한다")
    void createsTransactionNumber() {
        String number = plan.transactionNumber(LocalDate.of(2026, 8, 1), spec.transferCount());

        assertThat(number).isEqualTo("20260801WB0102900001");
        assertThat(plan.occurredAt(0).toLocalDate()).isEqualTo(plan.tradeDate(0));
        assertThat(plan.transferMonthlyCount(0) + plan.transferMonthlyCount(1) + plan.transferMonthlyCount(2))
                .isEqualTo(spec.transferCount());
        assertThat(plan.transferPaydayCount(0)).isLessThan(200_001);
    }

    @Test
    @DisplayName("이체 인덱스가 목표 건수를 넘으면 날짜를 만들지 않는다")
    void rejectsOutOfRangeTransferIndex() {
        assertThatThrownBy(() -> plan.tradeDate(spec.transferCount()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이체 인덱스");
    }
}
