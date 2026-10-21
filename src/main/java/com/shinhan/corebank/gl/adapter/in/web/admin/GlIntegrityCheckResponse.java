package com.shinhan.corebank.gl.adapter.in.web.admin;

import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.GlIntegrityReport;
import com.shinhan.corebank.gl.domain.LedgerVoucherMismatch;
import com.shinhan.corebank.gl.domain.UnbalancedVoucher;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record GlIntegrityCheckResponse(
        @Schema(description = "점검한 거래일", example = "2026-10-01") LocalDate tradeDate,
        @Schema(description = "원장↔전표 대사를 한 원장 거래 수", example = "46000") long checkedTransactionCount,
        @Schema(description = "대사 대상이 아니어서 건너뛴 원장 거래 수. 키는 원장 거래유형", example = "{\"INTERBANK_TRANSFER\": 12}")
                Map<String, Long> excludedTransactionCounts,
        @Schema(description = "차변 합계와 대변 합계가 다르거나 분개가 2줄 미만인 전표. 전표번호 순 최대 100건")
                List<UnbalancedVoucherRow> unbalancedVouchers,
        @Schema(description = "차대변 불일치 전표 전체 건수", example = "0") long unbalancedVoucherCount,
        @Schema(description = "원장 거래와 전표가 맞지 않는 건. 거래번호 순 최대 100건")
                List<LedgerVoucherMismatchRow> ledgerVoucherMismatches,
        @Schema(description = "원장↔전표 불일치 전체 건수", example = "0") long ledgerVoucherMismatchCount,
        @Schema(description = "원장 없이 전표만 있는 고아 전표 수. 목록은 내지 않는다", example = "0") long orphanVoucherCount,
        @Schema(description = "고아 전표 수가 확정값인지. 현재 영업일보다 앞선 거래일이면 true. false 면 점검 중 기표로 덜 셀 수 있는 참고값", example = "true")
                boolean orphanVoucherCountFinal,
        @Schema(description = "두 점검 모두 결함이 없는지", example = "true") boolean clean) {

    public record UnbalancedVoucherRow(
            @Schema(description = "전표번호", example = "20261001-TRF-000123") String voucherNo,
            @Schema(description = "전표유형", example = "TRANSFER") GlTxType txType,
            @Schema(description = "원 거래번호", example = "20261001WB0000000123") String referenceKey,
            @Schema(description = "차변 합계(원)", example = "10000") long debitTotal,
            @Schema(description = "대변 합계(원)", example = "0") long creditTotal,
            @Schema(description = "분개 줄 수", example = "1") long entryCount) {

        static UnbalancedVoucherRow from(UnbalancedVoucher voucher) {
            return new UnbalancedVoucherRow(
                    voucher.voucherNo(),
                    voucher.txType(),
                    voucher.referenceKey(),
                    voucher.debitTotal(),
                    voucher.creditTotal(),
                    voucher.entryCount());
        }
    }

    public record LedgerVoucherMismatchRow(
            @Schema(description = "원장 거래번호", example = "20261001WB0000000123") String transactionNumber,
            @Schema(description = "원장 거래유형", example = "IMMEDIATE_TRANSFER") String ledgerTransactionType,
            @Schema(
                            description = "MISSING_VOUCHER(전표 없음) / AMOUNT_MISMATCH(전표 금액이 원장과 다름)",
                            example = "MISSING_VOUCHER")
                    LedgerVoucherMismatch.Kind kind,
            @Schema(description = "원장 거래 금액(원)", example = "10000") long ledgerAmount,
            @Schema(description = "전표 차변 합계(원). 전표가 없으면 0", example = "0") long voucherDebitTotal,
            @Schema(description = "전표 대변 합계(원). 전표가 없으면 0", example = "0") long voucherCreditTotal) {

        static LedgerVoucherMismatchRow from(LedgerVoucherMismatch mismatch) {
            return new LedgerVoucherMismatchRow(
                    mismatch.transactionNumber(),
                    mismatch.ledgerTransactionType(),
                    mismatch.kind(),
                    mismatch.ledgerAmount(),
                    mismatch.voucherDebitTotal(),
                    mismatch.voucherCreditTotal());
        }
    }

    static GlIntegrityCheckResponse from(GlIntegrityReport report) {
        return new GlIntegrityCheckResponse(
                report.tradeDate(),
                report.checkedTransactionCount(),
                report.excludedTransactionCounts(),
                report.unbalancedVouchers().stream()
                        .map(UnbalancedVoucherRow::from)
                        .toList(),
                report.unbalancedVoucherCount(),
                report.ledgerVoucherMismatches().stream()
                        .map(LedgerVoucherMismatchRow::from)
                        .toList(),
                report.ledgerVoucherMismatchCount(),
                report.orphanVoucherCount(),
                report.orphanVoucherCountFinal(),
                report.clean());
    }
}
