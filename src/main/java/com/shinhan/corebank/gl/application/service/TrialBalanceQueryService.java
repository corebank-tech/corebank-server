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

    // 집계가 커넥션을 붙잡는 상한(초). 인덱스 없이 전 구간을 잰 운영 p95가 27.4초, 최댓값이 30.0초라
    // 인덱스가 빠진 상황에서도 정상 조회는 거의 자르지 않는 값이다(ph28_baseline_results.md §3).
    // 넘기면 JDBC 쿼리 타임아웃으로 끊기고 공통 오류(CMN9999)로 응답한다.
    static final int QUERY_TIMEOUT_SECONDS = 30;

    @Override
    @Transactional(readOnly = true, timeout = QUERY_TIMEOUT_SECONDS)
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
