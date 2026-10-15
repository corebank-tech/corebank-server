package com.shinhan.corebank.gl.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQuery;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQueryUseCase;
import com.shinhan.corebank.gl.application.port.out.TrialBalanceQueryPort;
import com.shinhan.corebank.gl.domain.TrialBalance;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrialBalanceQueryService implements TrialBalanceQueryUseCase {

    private static final int MAX_PERIOD_DAYS = 365;

    private final TrialBalanceQueryPort trialBalanceQueryPort;

    @Override
    @Transactional(readOnly = true)
    public TrialBalance query(TrialBalanceQuery query) {
        // 화면도 막지만 서버에서 다시 막는다 — 화면을 거치지 않은 요청이 분개 전 기간 집계를 돌리지 못하게.
        validatePeriod(query.fromDate(), query.toDate());
        return new TrialBalance(
                query.fromDate(), query.toDate(), trialBalanceQueryPort.sumByAccount(query.fromDate(), query.toDate()));
    }

    private void validatePeriod(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
        // 연 단위가 아니라 일수로 센다. FE checkPeriodRange 가 일수(365)로 막으므로 윤일이 낀 해에 경계가 갈리지 않게 한다.
        if (fromDate.plusDays(MAX_PERIOD_DAYS).isBefore(toDate)) {
            throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
        }
    }
}
