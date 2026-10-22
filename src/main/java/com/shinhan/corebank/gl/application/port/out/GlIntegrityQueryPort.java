package com.shinhan.corebank.gl.application.port.out;

import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.UnbalancedVoucher;
import com.shinhan.corebank.gl.domain.VoucherTotals;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface GlIntegrityQueryPort {

    /** 거래일 하루의 차대변 불일치 전표를 전표번호 순으로 최대 {@code limit} 건, 그리고 전체 건수. */
    UnbalancedVouchers findUnbalancedVouchers(LocalDate tradeDate, int limit);

    /**
     * 거래일 하루의 전표 중 전표유형이 {@code txTypes} 중 하나이고 참조 키가 {@code referenceKeys} 중 하나인 전표의 차변·대변
     * 합계. 다른 거래일 전표는 짝으로 보지 않는다 — 그 전표는 자기 거래일의 고아 전표로 센다.
     */
    List<VoucherTotals> sumVoucherTotals(
            LocalDate tradeDate, Collection<GlTxType> txTypes, Collection<String> referenceKeys);

    /** 거래일 하루의 전표 중 전표유형이 {@code txTypes} 중 하나인 전표 수. */
    long countVouchers(LocalDate tradeDate, Collection<GlTxType> txTypes);

    record UnbalancedVouchers(List<UnbalancedVoucher> vouchers, long totalCount) {}
}
