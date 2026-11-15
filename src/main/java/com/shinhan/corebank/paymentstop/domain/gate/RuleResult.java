package com.shinhan.corebank.paymentstop.domain.gate;

import java.util.List;

/**
 * 규칙 하나의 결과.
 *
 * @param matched 규칙 조건이 성립했는지
 * @param usedTransactionNumbers 판단에 쓴 원장 거래. 화면이 근거 거래로 연결한다
 * @param detail 화면에 보일 한 줄. 값과 기준을 함께 적는다
 */
public record RuleResult(GateRule rule, boolean matched, List<String> usedTransactionNumbers, String detail) {

    public RuleResult {
        usedTransactionNumbers = List.copyOf(usedTransactionNumbers);
    }
}
