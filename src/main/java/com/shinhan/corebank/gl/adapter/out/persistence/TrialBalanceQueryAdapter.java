package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.application.port.out.TrialBalanceQueryPort;
import com.shinhan.corebank.gl.domain.GlAccountClass;
import com.shinhan.corebank.gl.domain.GlNormalBalance;
import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrialBalanceQueryAdapter implements TrialBalanceQueryPort {

    private final GlJournalEntryJpaRepository journalEntryRepository;

    // 조회 트랜잭션 타임아웃(TrialBalanceQueryService)을 넘기면 JDBC 쿼리 타임아웃으로 끊긴다.
    // 공통 오류(CMN9999)의 "잠시 후 다시 시도"는 같은 시간을 또 기다리게 하므로, 기간을 줄이라는 GL 오류로 바꾼다.
    @Override
    public List<TrialBalanceLine> sumByAccount(LocalDate fromDate, LocalDate toDate) {
        try {
            return journalEntryRepository.sumByAccount(fromDate, toDate).stream()
                    .map(TrialBalanceQueryAdapter::toLine)
                    .toList();
        } catch (QueryTimeoutException e) {
            throw new BusinessException(GlErrorCode.TRIAL_BALANCE_QUERY_TIMEOUT, e);
        }
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
