package com.shinhan.corebank.paymentstop.domain.gate;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import java.util.List;

/**
 * 게이트 결과. 해제를 여는 판정인지는 {@link #allowsRelease(DecisionResult)}만 정한다.
 *
 * @param simplifiedExcluded 간소화 신청이었지만 지급정지 이력 때문에 일반 심사로 넘어갔는지
 * @param rules 규칙별 결과. 화면 체크리스트와 gate_evaluation 기록에 쓴다
 * @param rulesVersion 규칙 버전. 설정값과 함께 기록해 나중에 같은 판정을 재현한다(D10)
 */
public record GateResult(
        GateBranch branch,
        boolean simplifiedExcluded,
        List<RuleResult> rules,
        String rulesVersion,
        int clause,
        boolean litigationPending) {

    public GateResult {
        rules = List.copyOf(rules);
    }

    /**
     * 이 판정으로 지급정지를 줄여도 되는지. 불수용은 언제나 열리고, 줄이는 쪽은 근거가 있을 때만 열린다(D9).
     *
     * <p>해제 범위는 사유 조항이 정한다(D28). 수용은 1호, 2호일 때만, 일부 지급정지는 3호일 때만 연다. 소송 계속이면 둘 다
     * 닫는다(D31).
     */
    public boolean allowsRelease(DecisionResult result) {
        return switch (result) {
            case REJECTED -> true;
            case ACCEPTED -> !litigationPending && branch == GateBranch.NORMAL_ACCEPT && (clause == 1 || clause == 2);
            case PARTIAL ->
                !litigationPending
                        && (branch == GateBranch.NORMAL_ACCEPT || branch == GateBranch.NORMAL_PARTIAL)
                        && clause == 3;
        };
    }

    public RuleResult rule(GateRule rule) {
        return rules.stream()
                .filter(result -> result.rule() == rule)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("평가하지 않은 규칙입니다: " + rule));
    }
}
