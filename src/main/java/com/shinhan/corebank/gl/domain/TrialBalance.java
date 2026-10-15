package com.shinhan.corebank.gl.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 합계시산표 — 조회기간 안의 분개를 계정별로 합산한 표와 차대변 일치 판정 (PH-28).
 *
 * <p>기초·기말잔액은 두지 않는다. 기간 밖 분개를 전부 훑어야 해서 마감잔액 테이블이 필요한데 2차 범위 밖이다.
 * 전표 하나는 항상 차대가 맞고 분개는 전표와 같은 거래일을 가지므로(복합 FK), 어떤 기간을 잘라도 총계는 같아야 한다.
 *
 * <p>총계·차액·일치 여부는 저장하지 않고 행에서 계산한다. 저장하면 행과 맞지 않는 총계로 만들 수 있다.
 * 건수를 별도 쿼리로 세지 않는 것도 같은 이유다 — 두 쿼리 사이에 기표된 전표 때문에 행 합계와 어긋난다.
 */
public record TrialBalance(LocalDate fromDate, LocalDate toDate, List<TrialBalanceLine> rows) {

    public TrialBalance {
        rows = List.copyOf(rows);
    }

    // 넘치면 조용히 음수가 되어 일치 판정이 틀어진다. 예외로 드러낸다.
    public long debitGrandTotal() {
        return rows.stream().mapToLong(TrialBalanceLine::debitTotal).reduce(0L, Math::addExact);
    }

    public long creditGrandTotal() {
        return rows.stream().mapToLong(TrialBalanceLine::creditTotal).reduce(0L, Math::addExact);
    }

    /** 차변 총계 - 대변 총계. 0 이 아니면 장부가 틀렸다. */
    public long difference() {
        return Math.subtractExact(debitGrandTotal(), creditGrandTotal());
    }

    public boolean balanced() {
        return difference() == 0;
    }

    public long journalEntryCount() {
        return rows.stream().mapToLong(TrialBalanceLine::entryCount).reduce(0L, Math::addExact);
    }
}
