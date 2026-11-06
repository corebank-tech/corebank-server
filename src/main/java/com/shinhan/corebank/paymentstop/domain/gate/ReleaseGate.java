package com.shinhan.corebank.paymentstop.domain.gate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 해제 게이트. 원장 사실만으로 판정 갈래를 낸다. LLM과 화면은 이 계산에 끼어들 수 없다(D9, D10).
 *
 * <p>1차 범위(PA-04): 근거 제외, 간소화 제외(순서 0), 사기 패턴(순서 2, 3), 관계 인정(순서 4), 그 밖(순서 6). 외부 확인(순서
 * 1)과 일부 지급정지 요건(순서 5)은 PA-09에서 더한다.
 *
 * <p>근거 제외: 다툼 거래 전부와, 다툼 거래 중 가장 이른 시각 이후의 거래는 정상 근거로 쓰지 않는다. 다툼 뒤에 만든 거래로
 * 관계를 꾸밀 수 있기 때문이다. 「구매자와 송금인 일치」는 원장 사실이 아니라서 입력에 아예 없다. 경계값은 모두 이상, 이하를
 * 포함한다.
 */
public final class ReleaseGate {

    public static final String RULES_VERSION = "2026-11-03.1";

    private ReleaseGate() {}

    public static GateResult evaluate(GateFacts facts, GateSettings settings) {
        LocalDateTime firstDisputedAt = facts.disputedTransfers().stream()
                .map(LedgerFact::occurredAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        Set<String> disputedNumbers = new HashSet<>();
        facts.disputedTransfers().forEach(fact -> disputedNumbers.add(fact.transactionNumber()));

        List<LedgerFact> evidence = facts.accountTransfers().stream()
                .filter(fact -> !disputedNumbers.contains(fact.transactionNumber()))
                .filter(fact -> fact.occurredAt().isBefore(firstDisputedAt))
                .toList();

        boolean simplifiedExcluded = facts.simplifiedRequested() && facts.otherStopHistory();
        RuleResult simplified = new RuleResult(
                GateRule.SIMPLIFIED_EXCLUSION,
                simplifiedExcluded,
                List.of(),
                !facts.simplifiedRequested()
                        ? "간소화 신청 없음"
                        : facts.otherStopHistory() ? "간소화 신청, 지급정지 이력 있음" : "간소화 신청, 지급정지 이력 없음");

        RuleResult fraud = fraudPattern(facts, settings);
        RuleResult relationship = relationship(facts, evidence, firstDisputedAt, settings);

        GateBranch branch;
        if (fraud.matched()) {
            // 순서 2와 3: 사기 패턴에 정상 근거가 겹치면 충돌이라 판단 불가로 둔다
            branch = relationship.matched() ? GateBranch.UNDETERMINED : GateBranch.FRAUD_PATTERN;
        } else if (relationship.matched()) {
            branch = GateBranch.NORMAL_ACCEPT;
        } else {
            branch = GateBranch.UNDETERMINED;
        }

        return new GateResult(
                branch,
                simplifiedExcluded,
                List.of(simplified, fraud, relationship),
                RULES_VERSION,
                facts.clause(),
                facts.litigationPending());
    }

    /**
     * 다툼 거래 전후 창 안에 기준액 이상 입금의 서로 다른 송금인이 기준 수 이상이고, 그 입금 합의 기준 비율 이상이 각 입금 직후
     * 출금됐으면 성립한다. 출금은 시간순으로 입금에 한 번씩만 배정해 겹쳐 세지 않는다.
     *
     * <p>타행 입금은 송금인을 구별할 수 없어 거래마다 다른 송금인으로 센다. 판단이 불수용 쪽으로 기울지만, 막는 쪽은 언제나 열려
     * 있다는 원칙(D9)에 맞는 방향이다.
     */
    private static RuleResult fraudPattern(GateFacts facts, GateSettings settings) {
        Map<String, LedgerFact> pool = new LinkedHashMap<>();
        facts.accountTransfers().forEach(fact -> pool.putIfAbsent(fact.transactionNumber(), fact));
        facts.disputedTransfers().forEach(fact -> pool.putIfAbsent(fact.transactionNumber(), fact));

        LocalDateTime firstDisputedAt = facts.disputedTransfers().stream()
                .map(LedgerFact::occurredAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDateTime lastDisputedAt = facts.disputedTransfers().stream()
                .map(LedgerFact::occurredAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        LocalDateTime windowStart = firstDisputedAt.minusHours(settings.fraudWindowHours());
        LocalDateTime windowEnd = lastDisputedAt.plusHours(settings.fraudWindowHours());

        List<LedgerFact> deposits = pool.values().stream()
                .filter(LedgerFact::incoming)
                .filter(fact -> fact.amount() >= settings.fraudMinDeposit())
                .filter(fact -> within(fact.occurredAt(), windowStart, windowEnd))
                .sorted(Comparator.comparing(LedgerFact::occurredAt))
                .toList();

        Set<String> senders = new HashSet<>();
        deposits.forEach(deposit -> senders.add(
                deposit.counterpartyAccountId() != null
                        ? "account:" + deposit.counterpartyAccountId()
                        : "interbank:" + deposit.transactionNumber()));

        List<LedgerFact> withdrawals = pool.values().stream()
                .filter(fact -> !fact.incoming())
                .sorted(Comparator.comparing(LedgerFact::occurredAt))
                .toList();
        long[] remaining = withdrawals.stream().mapToLong(LedgerFact::amount).toArray();

        List<String> used = new ArrayList<>();
        long depositTotal = 0L;
        long covered = 0L;
        for (LedgerFact deposit : deposits) {
            used.add(deposit.transactionNumber());
            depositTotal += deposit.amount();

            long need = deposit.amount();
            LocalDateTime outflowEnd = deposit.occurredAt().plusMinutes(settings.fraudOutflowMinutes());
            for (int i = 0; i < withdrawals.size() && need > 0; i++) {
                LedgerFact withdrawal = withdrawals.get(i);
                if (remaining[i] == 0 || !within(withdrawal.occurredAt(), deposit.occurredAt(), outflowEnd)) {
                    continue;
                }
                long take = Math.min(need, remaining[i]);
                remaining[i] -= take;
                need -= take;
                covered += take;
                if (!used.contains(withdrawal.transactionNumber())) {
                    used.add(withdrawal.transactionNumber());
                }
            }
        }

        boolean enoughSenders = senders.size() >= settings.fraudMinSenders();
        boolean fastOutflow = depositTotal > 0 && covered * 100 >= depositTotal * settings.fraudOutflowPercent();
        String detail = "송금인 %d명(기준 %d명 이상), 즉시 출금 %d원 / 입금 %d원(기준 %d%% 이상)"
                .formatted(
                        senders.size(),
                        settings.fraudMinSenders(),
                        covered,
                        depositTotal,
                        settings.fraudOutflowPercent());

        return new RuleResult(GateRule.FRAUD_PATTERN, enoughSenders && fastOutflow, used, detail);
    }

    /**
     * 송금인과 명의인 사이 당행 거래(양방향)가 다툼 이전 조회 기간 안에 기준 횟수 이상이고, 기간이 충분히 길고, 다툼보다 충분히
     * 앞서 시작했고, 다툼 금액이 과거 관계 거래 규모에 비해 지나치게 크지 않으면 성립한다.
     */
    private static RuleResult relationship(
            GateFacts facts, List<LedgerFact> evidence, LocalDateTime firstDisputedAt, GateSettings settings) {
        if (facts.senderAccountId() == null) {
            return new RuleResult(GateRule.RELATIONSHIP, false, List.of(), "타행 송금이라 당행 관계 거래를 볼 수 없음");
        }

        long senderAccountId = facts.senderAccountId();
        LocalDateTime lookbackStart = firstDisputedAt.minusDays(settings.relationLookbackDays());

        List<LedgerFact> relation = evidence.stream()
                .filter(fact -> fact.isWithCounterparty(senderAccountId))
                .filter(fact -> !fact.occurredAt().isBefore(lookbackStart))
                .sorted(Comparator.comparing(LedgerFact::occurredAt))
                .toList();

        long disputedTotal =
                facts.disputedTransfers().stream().mapToLong(LedgerFact::amount).sum();

        if (relation.isEmpty()) {
            return new RuleResult(GateRule.RELATIONSHIP, false, List.of(), "다툼 이전 관계 거래 0회");
        }

        LocalDateTime first = relation.getFirst().occurredAt();
        LocalDateTime last = relation.getLast().occurredAt();
        long spanDays = ChronoUnit.DAYS.between(first, last);
        long leadDays = ChronoUnit.DAYS.between(first, firstDisputedAt);
        long maxAmount = relation.stream().mapToLong(LedgerFact::amount).max().orElseThrow();

        boolean matched = relation.size() >= settings.relationMinCount()
                && spanDays >= settings.relationMinSpanDays()
                && leadDays >= settings.relationMinLeadDays()
                && disputedTotal <= maxAmount * settings.relationMaxAmountMultiple();

        String detail = "관계 거래 %d회(기준 %d회 이상), 기간 %d일(기준 %d일 이상), 다툼보다 %d일 앞섬(기준 %d일 이상), 다툼 %d원 / 최대 %d원 × %d"
                .formatted(
                        relation.size(),
                        settings.relationMinCount(),
                        spanDays,
                        settings.relationMinSpanDays(),
                        leadDays,
                        settings.relationMinLeadDays(),
                        disputedTotal,
                        maxAmount,
                        settings.relationMaxAmountMultiple());

        return new RuleResult(
                GateRule.RELATIONSHIP,
                matched,
                relation.stream().map(LedgerFact::transactionNumber).toList(),
                detail);
    }

    private static boolean within(LocalDateTime at, LocalDateTime start, LocalDateTime end) {
        return !at.isBefore(start) && !at.isAfter(end);
    }
}
