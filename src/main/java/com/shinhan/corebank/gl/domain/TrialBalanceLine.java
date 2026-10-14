package com.shinhan.corebank.gl.domain;

import java.util.Objects;

/**
 * 시산표 한 행 — 조회기간 안에서 한 계정이 받은 차변·대변 합계.
 *
 * <p>계정과목에 없는 코드면 {@code accountName}·{@code accountClass}·{@code normalBalance} 가 {@code null} 이다.
 * 코드 첫 자리로 분류를 추측해 채우지 않는다 — 미등록이라는 사실을 그대로 드러내는 것이 이 행의 목적이다
 * (FE REQ-ADM-024).
 */
public record TrialBalanceLine(
        String accountCode,
        String accountName,
        GlAccountClass accountClass,
        GlNormalBalance normalBalance,
        long debitTotal,
        long creditTotal,
        long entryCount) {

    public TrialBalanceLine {
        Objects.requireNonNull(accountCode, "accountCode must not be null");
    }
}
