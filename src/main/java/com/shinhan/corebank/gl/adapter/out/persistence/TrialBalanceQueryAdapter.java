package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.application.port.out.TrialBalanceQueryPort;
import com.shinhan.corebank.gl.domain.GlAccountClass;
import com.shinhan.corebank.gl.domain.GlNormalBalance;
import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrialBalanceQueryAdapter implements TrialBalanceQueryPort {

    private final GlJournalEntryJpaRepository journalEntryRepository;

    @Override
    public List<TrialBalanceLine> sumByAccount(LocalDate fromDate, LocalDate toDate) {
        return journalEntryRepository.sumByAccount(fromDate, toDate).stream()
                .map(TrialBalanceQueryAdapter::toLine)
                .toList();
    }

    // 미등록 계정이면 LEFT JOIN 결과가 null 이다. 분류를 추측해 채우지 않고 null 로 넘긴다.
    private static TrialBalanceLine toLine(TrialBalanceRowProjection row) {
        return new TrialBalanceLine(
                row.getAccountCode(),
                row.getAccountName(),
                row.getAccountClass() == null ? null : GlAccountClass.valueOf(row.getAccountClass()),
                row.getNormalBalance() == null ? null : GlNormalBalance.valueOf(row.getNormalBalance()),
                row.getDebitTotal().longValueExact(),
                row.getCreditTotal().longValueExact(),
                row.getEntryCount());
    }
}
