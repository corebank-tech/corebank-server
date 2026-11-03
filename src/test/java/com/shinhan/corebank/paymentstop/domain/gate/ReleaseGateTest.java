package com.shinhan.corebank.paymentstop.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReleaseGateTest {

    private static final LocalDateTime DISPUTED_AT = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final long SENDER = 500L;
    private static final GateSettings SETTINGS = GateSettings.defaults();

    private final AtomicInteger sequence = new AtomicInteger();

    @Nested
    @DisplayName("사기 패턴")
    class FraudPattern {

        @Test
        @DisplayName("송금인 3명 입금의 80%가 1시간 안에 빠져나가면 불수용 후보다")
        void threeSendersAndEightyPercentOutflow_isFraud() {
            List<LedgerFact> account = new ArrayList<>();
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);
            account.add(internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(10)));
            account.add(internalIn(602L, 100_000L, DISPUTED_AT.plusMinutes(20)));
            account.add(interbankOut(240_000L, DISPUTED_AT.plusMinutes(30)));

            GateResult result = evaluate(List.of(disputed), account, SENDER);

            assertThat(result.branch()).isEqualTo(GateBranch.FRAUD_PATTERN);
            assertThat(result.rule(GateRule.FRAUD_PATTERN).matched()).isTrue();
        }

        @Test
        @DisplayName("출금 비율이 79%면 사기 패턴이 아니다")
        void seventyNinePercentOutflow_isNotFraud() {
            List<LedgerFact> account = new ArrayList<>();
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);
            account.add(internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(10)));
            account.add(internalIn(602L, 100_000L, DISPUTED_AT.plusMinutes(20)));
            account.add(interbankOut(237_000L, DISPUTED_AT.plusMinutes(30)));

            GateResult result = evaluate(List.of(disputed), account, SENDER);

            assertThat(result.rule(GateRule.FRAUD_PATTERN).matched()).isFalse();
            assertThat(result.branch()).isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("송금인이 2명이면 사기 패턴이 아니다")
        void twoSenders_isNotFraud() {
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);
            List<LedgerFact> account = List.of(
                    internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(10)),
                    internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(20)),
                    interbankOut(300_000L, DISPUTED_AT.plusMinutes(30)));

            assertThat(evaluate(List.of(disputed), account, SENDER)
                            .rule(GateRule.FRAUD_PATTERN)
                            .matched())
                    .isFalse();
        }

        @Test
        @DisplayName("1만 원 미만 입금은 송금인으로 세지 않는다")
        void depositBelowMinimum_isNotCounted() {
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);
            List<LedgerFact> account = List.of(
                    internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(10)),
                    internalIn(602L, 9_999L, DISPUTED_AT.plusMinutes(20)),
                    interbankOut(209_999L, DISPUTED_AT.plusMinutes(30)));

            assertThat(evaluate(List.of(disputed), account, SENDER)
                            .rule(GateRule.FRAUD_PATTERN)
                            .matched())
                    .isFalse();
        }

        @Test
        @DisplayName("다툼 거래 24시간 뒤 입금은 창 안이고 24시간 1분 뒤는 창 밖이다")
        void windowBoundary_isInclusive() {
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);

            List<LedgerFact> inside = List.of(
                    internalIn(601L, 100_000L, DISPUTED_AT.minusHours(24)),
                    interbankOut(100_000L, DISPUTED_AT.minusHours(24).plusMinutes(5)),
                    internalIn(602L, 100_000L, DISPUTED_AT.plusHours(24)),
                    interbankOut(100_000L, DISPUTED_AT.plusHours(24).plusMinutes(5)),
                    interbankOut(100_000L, DISPUTED_AT.plusMinutes(5)));
            assertThat(evaluate(List.of(disputed), inside, SENDER)
                            .rule(GateRule.FRAUD_PATTERN)
                            .matched())
                    .isTrue();

            List<LedgerFact> outside = List.of(
                    internalIn(601L, 100_000L, DISPUTED_AT.minusHours(24)),
                    interbankOut(100_000L, DISPUTED_AT.minusHours(24).plusMinutes(5)),
                    internalIn(602L, 100_000L, DISPUTED_AT.plusHours(24).plusMinutes(1)),
                    interbankOut(100_000L, DISPUTED_AT.plusHours(24).plusMinutes(6)),
                    interbankOut(100_000L, DISPUTED_AT.plusMinutes(5)));
            assertThat(evaluate(List.of(disputed), outside, SENDER)
                            .rule(GateRule.FRAUD_PATTERN)
                            .matched())
                    .isFalse();
        }

        @Test
        @DisplayName("입금 60분 뒤 출금은 즉시 출금이고 61분 뒤는 아니다")
        void outflowMinutesBoundary_isInclusive() {
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);
            List<LedgerFact> senders =
                    List.of(internalIn(601L, 100_000L, DISPUTED_AT), internalIn(602L, 100_000L, DISPUTED_AT));

            List<LedgerFact> atSixty = new ArrayList<>(senders);
            atSixty.add(interbankOut(300_000L, DISPUTED_AT.plusMinutes(60)));
            assertThat(evaluate(List.of(disputed), atSixty, SENDER)
                            .rule(GateRule.FRAUD_PATTERN)
                            .matched())
                    .isTrue();

            List<LedgerFact> atSixtyOne = new ArrayList<>(senders);
            atSixtyOne.add(interbankOut(300_000L, DISPUTED_AT.plusMinutes(61)));
            assertThat(evaluate(List.of(disputed), atSixtyOne, SENDER)
                            .rule(GateRule.FRAUD_PATTERN)
                            .matched())
                    .isFalse();
        }

        @Test
        @DisplayName("한 번의 출금을 여러 입금에 겹쳐 세지 않는다")
        void singleWithdrawal_isNotDoubleCounted() {
            LedgerFact disputed = internalIn(SENDER, 100_000L, DISPUTED_AT);
            List<LedgerFact> account = List.of(
                    internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(1)),
                    internalIn(602L, 100_000L, DISPUTED_AT.plusMinutes(2)),
                    interbankOut(100_000L, DISPUTED_AT.plusMinutes(3)));

            RuleResult fraud = evaluate(List.of(disputed), account, SENDER).rule(GateRule.FRAUD_PATTERN);

            assertThat(fraud.matched()).isFalse();
            assertThat(fraud.detail()).contains("즉시 출금 100000원 / 입금 300000원");
        }

        @Test
        @DisplayName("타행 입금은 거래마다 다른 송금인으로 센다")
        void interbankDeposits_countAsDistinctSenders() {
            LedgerFact disputed = interbankIn(100_000L, DISPUTED_AT);
            List<LedgerFact> account = List.of(
                    interbankIn(100_000L, DISPUTED_AT.plusMinutes(10)),
                    interbankIn(100_000L, DISPUTED_AT.plusMinutes(20)),
                    interbankOut(300_000L, DISPUTED_AT.plusMinutes(30)));

            assertThat(evaluate(List.of(disputed), account, null).branch()).isEqualTo(GateBranch.FRAUD_PATTERN);
        }
    }

    @Nested
    @DisplayName("관계 인정")
    class Relationship {

        @Test
        @DisplayName("다툼 30일 전부터 30일 동안 관계 거래 3회면 수용 후보다")
        void threeTransfersOverThirtyDays_isAccepted() {
            GateResult result = evaluate(List.of(disputed(500_000L)), relation(100_000L, 30, 15, 0), SENDER);

            assertThat(result.branch()).isEqualTo(GateBranch.NORMAL_ACCEPT);
            assertThat(result.rule(GateRule.RELATIONSHIP).usedTransactionNumbers())
                    .hasSize(3);
        }

        @Test
        @DisplayName("관계 거래가 2회면 인정하지 않는다")
        void twoTransfers_isNotRelationship() {
            List<LedgerFact> account = new ArrayList<>(relation(100_000L, 30, 0));

            assertThat(evaluate(List.of(disputed(100_000L)), account, SENDER).branch())
                    .isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("첫 거래와 마지막 거래 간격이 29일이면 인정하지 않는다")
        void spanBelowMinimum_isNotRelationship() {
            assertThat(evaluate(List.of(disputed(100_000L)), relation(100_000L, 31, 15, 2), SENDER)
                            .branch())
                    .isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("첫 관계 거래가 다툼보다 29일 앞서면 인정하지 않는다")
        void leadBelowMinimum_isNotRelationship() {
            assertThat(evaluate(List.of(disputed(100_000L)), relation(100_000L, 29, 15, 0), SENDER)
                            .branch())
                    .isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("다툼 금액 합이 관계 거래 최대액의 5배면 인정하고 1원 넘으면 인정하지 않는다")
        void amountMultipleBoundary_isInclusive() {
            assertThat(evaluate(List.of(disputed(500_000L)), relation(100_000L, 30, 15, 0), SENDER)
                            .branch())
                    .isEqualTo(GateBranch.NORMAL_ACCEPT);
            assertThat(evaluate(List.of(disputed(500_001L)), relation(100_000L, 30, 15, 0), SENDER)
                            .branch())
                    .isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("다툼 이전 180일보다 오래된 관계 거래는 세지 않는다")
        void transferBeforeLookback_isNotCounted() {
            List<LedgerFact> rest = List.of(
                    internalOut(SENDER, 100_000L, DISPUTED_AT.minusDays(100)),
                    internalIn(SENDER, 100_000L, DISPUTED_AT.minusDays(2)));

            List<LedgerFact> atBoundary = new ArrayList<>(rest);
            atBoundary.add(internalIn(SENDER, 100_000L, DISPUTED_AT.minusDays(180)));
            assertThat(evaluate(List.of(disputed(100_000L)), atBoundary, SENDER).branch())
                    .isEqualTo(GateBranch.NORMAL_ACCEPT);

            List<LedgerFact> beyond = new ArrayList<>(rest);
            beyond.add(internalIn(SENDER, 100_000L, DISPUTED_AT.minusDays(180).minusMinutes(1)));
            assertThat(evaluate(List.of(disputed(100_000L)), beyond, SENDER).branch())
                    .isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("다툼 거래 이후의 거래는 관계 근거로 쓰지 않는다")
        void transfersAfterDispute_areExcluded() {
            List<LedgerFact> account = new ArrayList<>(relation(100_000L, 30, 15));
            account.add(internalIn(SENDER, 100_000L, DISPUTED_AT.plusMinutes(1)));

            assertThat(evaluate(List.of(disputed(100_000L)), account, SENDER).branch())
                    .isEqualTo(GateBranch.UNDETERMINED);
        }

        @Test
        @DisplayName("다툼 거래만 근거로는 수용하지 않는다(설계 불변식 4)")
        void disputedTransfersOnly_isNotAccepted() {
            List<LedgerFact> disputed = List.of(
                    internalIn(SENDER, 100_000L, DISPUTED_AT.minusDays(40)),
                    internalIn(SENDER, 100_000L, DISPUTED_AT.minusDays(20)),
                    internalIn(SENDER, 100_000L, DISPUTED_AT));

            GateResult result = evaluate(disputed, disputed, SENDER);

            assertThat(result.branch()).isEqualTo(GateBranch.UNDETERMINED);
            assertThat(result.allowsRelease(DecisionResult.ACCEPTED)).isFalse();
        }

        @Test
        @DisplayName("타행 송금이면 관계를 볼 수 없다")
        void interbankSender_hasNoRelationship() {
            assertThat(evaluate(List.of(interbankIn(100_000L, DISPUTED_AT)), List.of(), null)
                            .rule(GateRule.RELATIONSHIP)
                            .detail())
                    .contains("타행 송금");
        }

        @Test
        @DisplayName("사기 패턴과 관계가 함께 성립하면 판단 불가로 둔다")
        void fraudWithRelationship_isUndetermined() {
            // 마지막 관계 거래를 사기 패턴 창(전후 24시간) 밖에 둔다
            List<LedgerFact> account = new ArrayList<>(relation(100_000L, 40, 20, 2));
            account.add(internalIn(601L, 100_000L, DISPUTED_AT.plusMinutes(10)));
            account.add(internalIn(602L, 100_000L, DISPUTED_AT.plusMinutes(20)));
            account.add(interbankOut(300_000L, DISPUTED_AT.plusMinutes(30)));

            GateResult result = evaluate(List.of(disputed(100_000L)), account, SENDER);

            assertThat(result.rule(GateRule.FRAUD_PATTERN).matched()).isTrue();
            assertThat(result.rule(GateRule.RELATIONSHIP).matched()).isTrue();
            assertThat(result.branch()).isEqualTo(GateBranch.UNDETERMINED);
        }
    }

    @Nested
    @DisplayName("해제 허용")
    class AllowsRelease {

        @Test
        @DisplayName("수용 후보에 사유 1호, 2호면 수용만 열린다")
        void normalAcceptWithClauseOneOrTwo_opensAcceptedOnly() {
            for (int clause : new int[] {1, 2}) {
                GateResult result = acceptCandidate(clause, false);

                assertThat(result.allowsRelease(DecisionResult.ACCEPTED)).isTrue();
                assertThat(result.allowsRelease(DecisionResult.PARTIAL)).isFalse();
                assertThat(result.allowsRelease(DecisionResult.REJECTED)).isTrue();
            }
        }

        @Test
        @DisplayName("수용 후보라도 사유 3호면 일부 지급정지만 열린다")
        void normalAcceptWithClauseThree_opensPartialOnly() {
            GateResult result = acceptCandidate(3, false);

            assertThat(result.allowsRelease(DecisionResult.ACCEPTED)).isFalse();
            assertThat(result.allowsRelease(DecisionResult.PARTIAL)).isTrue();
        }

        @Test
        @DisplayName("소송 계속 중이면 수용과 일부 지급정지를 모두 닫는다")
        void litigationPending_closesRelease() {
            assertThat(acceptCandidate(1, true).allowsRelease(DecisionResult.ACCEPTED))
                    .isFalse();
            assertThat(acceptCandidate(3, true).allowsRelease(DecisionResult.PARTIAL))
                    .isFalse();
            assertThat(acceptCandidate(3, true).allowsRelease(DecisionResult.REJECTED))
                    .isTrue();
        }

        @Test
        @DisplayName("근거가 없으면 수용할 수 없다(설계 불변식 1)")
        void noEvidence_closesAccepted() {
            GateResult result = evaluate(List.of(disputed(100_000L)), List.of(), SENDER);

            assertThat(result.branch()).isEqualTo(GateBranch.UNDETERMINED);
            assertThat(result.allowsRelease(DecisionResult.ACCEPTED)).isFalse();
            assertThat(result.allowsRelease(DecisionResult.PARTIAL)).isFalse();
            assertThat(result.allowsRelease(DecisionResult.REJECTED)).isTrue();
        }

        private GateResult acceptCandidate(int clause, boolean litigationPending) {
            GateFacts facts = new GateFacts(
                    List.of(disputed(100_000L)),
                    relation(100_000L, 30, 15, 0),
                    SENDER,
                    false,
                    false,
                    clause,
                    litigationPending);
            GateResult result = ReleaseGate.evaluate(facts, SETTINGS);
            assertThat(result.branch()).isEqualTo(GateBranch.NORMAL_ACCEPT);
            return result;
        }
    }

    @Test
    @DisplayName("간소화 신청이라도 다른 지급정지 이력이 있으면 간소화 대상에서 뺀다")
    void simplifiedWithHistory_isExcluded() {
        GateFacts facts = new GateFacts(List.of(disputed(100_000L)), List.of(), SENDER, true, true, 3, false);

        GateResult result = ReleaseGate.evaluate(facts, SETTINGS);

        assertThat(result.simplifiedExcluded()).isTrue();
        assertThat(result.rule(GateRule.SIMPLIFIED_EXCLUSION).matched()).isTrue();
    }

    @Test
    @DisplayName("결과에 규칙 버전을 남긴다")
    void result_recordsRulesVersion() {
        assertThat(evaluate(List.of(disputed(100_000L)), List.of(), SENDER).rulesVersion())
                .isEqualTo(ReleaseGate.RULES_VERSION);
    }

    @Test
    @DisplayName("다툼 거래가 입금이 아니면 입력을 거부한다")
    void outgoingDisputedTransfer_isRejected() {
        assertThatThrownBy(() -> new GateFacts(
                        List.of(interbankOut(100_000L, DISPUTED_AT)), List.of(), SENDER, false, false, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GateResult evaluate(List<LedgerFact> disputed, List<LedgerFact> account, Long sender) {
        return ReleaseGate.evaluate(new GateFacts(disputed, account, sender, false, false, 1, false), SETTINGS);
    }

    private LedgerFact disputed(long amount) {
        return internalIn(SENDER, amount, DISPUTED_AT);
    }

    // 송금인과의 관계 거래. 인자는 다툼보다 며칠 앞서는지. 입금과 출금을 번갈아 둔다
    private List<LedgerFact> relation(long amount, int... daysBefore) {
        List<LedgerFact> facts = new ArrayList<>();
        for (int i = 0; i < daysBefore.length; i++) {
            LocalDateTime at = DISPUTED_AT.minusDays(daysBefore[i]).minusHours(1);
            facts.add(i % 2 == 0 ? internalIn(SENDER, amount, at) : internalOut(SENDER, amount, at));
        }
        return facts;
    }

    private LedgerFact internalIn(long counterparty, long amount, LocalDateTime at) {
        return new LedgerFact(next(), true, false, counterparty, amount, at);
    }

    private LedgerFact internalOut(long counterparty, long amount, LocalDateTime at) {
        return new LedgerFact(next(), false, false, counterparty, amount, at);
    }

    private LedgerFact interbankIn(long amount, LocalDateTime at) {
        return new LedgerFact(next(), true, true, null, amount, at);
    }

    private LedgerFact interbankOut(long amount, LocalDateTime at) {
        return new LedgerFact(next(), false, true, null, amount, at);
    }

    private String next() {
        return "T%019d".formatted(sequence.incrementAndGet());
    }
}
