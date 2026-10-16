package com.shinhan.corebank.gl.adapter.in.web.admin;

import com.shinhan.corebank.gl.domain.GlAccountClass;
import com.shinhan.corebank.gl.domain.GlNormalBalance;
import com.shinhan.corebank.gl.domain.TrialBalance;
import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

// 필드명은 FE TrialBalanceResult · TrialBalanceRow(src/entities/gl/lib/aggregate-trial-balance.ts)와 같다.
// 바꾸려면 FE 와 먼저 맞춘다(tasks.md §PH-28).
public record TrialBalanceResponse(
        @Schema(description = "조회 시작일(포함)", example = "2026-09-01") LocalDate fromDate,
        @Schema(description = "조회 종료일(포함)", example = "2026-09-30") LocalDate toDate,
        @Schema(description = "계정별 집계 행. 계정코드 오름차순. 분개가 없는 계정은 없다") List<Row> rows,
        @Schema(description = "차변 총계(원)", example = "1000000") long debitGrandTotal,
        @Schema(description = "대변 총계(원)", example = "1000000") long creditGrandTotal,
        @Schema(description = "차변 총계 - 대변 총계(원). 0 이 아니면 장부가 틀렸다", example = "0") long difference,
        @Schema(description = "차대변 일치 여부", example = "true") boolean balanced,
        @Schema(description = "집계한 분개 줄 수", example = "2") long journalEntryCount) {

    public record Row(
            @Schema(description = "계정과목 코드", example = "20100") String accountCode,
            @Schema(description = "계정과목명. 계정과목에 없는 코드면 null", example = "예수금", nullable = true) String accountName,
            @Schema(
                            description = "계정 분류. ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE. 미등록 코드면 null",
                            example = "LIABILITY",
                            nullable = true)
                    GlAccountClass accountClass,
            @Schema(description = "정상잔액 방향. DEBIT / CREDIT. 미등록 코드면 null", example = "CREDIT", nullable = true)
                    GlNormalBalance normalBalance,
            @Schema(description = "기간 안 차변 합계(원)", example = "1000000") long debitTotal,
            @Schema(description = "기간 안 대변 합계(원)", example = "1000000") long creditTotal,
            @Schema(description = "이 계정에 걸린 분개 줄 수", example = "2") long entryCount) {

        static Row from(TrialBalanceLine line) {
            return new Row(
                    line.accountCode(),
                    line.accountName(),
                    line.accountClass(),
                    line.normalBalance(),
                    line.debitTotal(),
                    line.creditTotal(),
                    line.entryCount());
        }
    }

    static TrialBalanceResponse from(TrialBalance trialBalance) {
        return new TrialBalanceResponse(
                trialBalance.fromDate(),
                trialBalance.toDate(),
                trialBalance.rows().stream().map(Row::from).toList(),
                trialBalance.debitGrandTotal(),
                trialBalance.creditGrandTotal(),
                trialBalance.difference(),
                trialBalance.balanced(),
                trialBalance.journalEntryCount());
    }
}
