package com.shinhan.corebank.gl.domain;

import com.shinhan.corebank.gl.api.GlTxType;
import java.util.Optional;
import java.util.Set;

/**
 * 원장 거래유형 → 전표유형 (gl_journal_patterns.md §3). 훅 B 가 기표하는 유형이 곧 원장↔전표 대사 대상이다.
 *
 * <p>훅과 대사가 이 한 곳을 쓴다. 따로 두면 훅에 유형을 더할 때 대사가 그 유형을 "대상 밖"으로 세어 누락을 놓친다.
 * 여기 없는 유형(타행이체, 예적금 입금 등)은 훅을 거치지 않거나 전표 규칙이 달라 대사하지 않는다(ADR-0009).
 */
public final class LedgerVoucherTypes {

    /** 원장↔전표 대사 대상 전표유형. {@link #voucherTypeOf} 가 돌려줄 수 있는 값 전부다. */
    public static final Set<GlTxType> RECONCILED_VOUCHER_TYPES =
            Set.of(GlTxType.TRANSFER, GlTxType.PRODUCT_SUBSCRIPTION, GlTxType.REVERSAL);

    private LedgerVoucherTypes() {}

    public static Optional<GlTxType> voucherTypeOf(String ledgerTransactionType) {
        if (ledgerTransactionType == null) {
            return Optional.empty();
        }
        return switch (ledgerTransactionType) {
            case "IMMEDIATE_TRANSFER", "SCHEDULED_TRANSFER", "AUTO_TRANSFER" -> Optional.of(GlTxType.TRANSFER);
            case "PRODUCT_SUBSCRIPTION" -> Optional.of(GlTxType.PRODUCT_SUBSCRIPTION);
            case "REVERSAL" -> Optional.of(GlTxType.REVERSAL);
            default -> Optional.empty();
        };
    }
}
