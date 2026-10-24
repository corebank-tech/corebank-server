package com.shinhan.corebank.gl.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 거래일 하루의 GL 정합성 점검 결과 (PH-28b). 전표 차대변 점검과 원장↔전표 대사를 함께 담는다.
 *
 * <p>원장 없이 전표만 있는 경우(고아 전표)는 건수로만 낸다. 그 거래일의 대상 유형 전표 수에서 원장 거래와 짝지어진 전표
 * 수를 뺀 값이다. 아직 마감되지 않은 거래일은 점검 중에 새 기표가 들어와 덜 셀 수 있어 참고값이고, 현재 영업일보다 앞선
 * 거래일만 확정값이다({@code orphanVoucherCountFinal}).
 *
 * <p>목록은 상한까지만 담고 건수는 전체를 센다. 대사하지 않은 원장 거래는 유형별 건수로 남긴다 — 건수 없이 빼면
 * 대사 범위가 줄어든 것이 드러나지 않는다.
 *
 * @param checkedTransactionCount 원장↔전표 대사를 한 원장 거래 수
 * @param excludedTransactionCounts 대사 대상이 아니어서 건너뛴 원장 거래 수(원장 유형별)
 */
public record GlIntegrityReport(
        LocalDate tradeDate,
        long checkedTransactionCount,
        Map<String, Long> excludedTransactionCounts,
        List<UnbalancedVoucher> unbalancedVouchers,
        long unbalancedVoucherCount,
        List<LedgerVoucherMismatch> ledgerVoucherMismatches,
        long ledgerVoucherMismatchCount,
        long orphanVoucherCount,
        boolean orphanVoucherCountFinal) {

    public GlIntegrityReport {
        excludedTransactionCounts = Map.copyOf(excludedTransactionCounts);
        unbalancedVouchers = List.copyOf(unbalancedVouchers);
        ledgerVoucherMismatches = List.copyOf(ledgerVoucherMismatches);
    }

    /** 두 점검 모두 결함이 없다. 고아 전표 참고값도 0 이어야 한다. */
    public boolean clean() {
        return unbalancedVoucherCount == 0 && ledgerVoucherMismatchCount == 0 && orphanVoucherCount == 0;
    }
}
