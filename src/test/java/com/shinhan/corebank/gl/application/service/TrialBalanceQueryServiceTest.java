package com.shinhan.corebank.gl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQuery;
import com.shinhan.corebank.gl.application.port.out.TrialBalanceQueryPort;
import com.shinhan.corebank.gl.domain.GlAccountClass;
import com.shinhan.corebank.gl.domain.GlNormalBalance;
import com.shinhan.corebank.gl.domain.TrialBalance;
import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrialBalanceQueryService 단위 테스트")
class TrialBalanceQueryServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

    @Mock
    private TrialBalanceQueryPort trialBalanceQueryPort;

    @InjectMocks
    private TrialBalanceQueryService service;

    @Test
    @DisplayName("포트가 준 계정별 행으로 시산표를 만든다")
    void buildsTrialBalanceFromPortRows() {
        LocalDate to = LocalDate.of(2026, 9, 30);
        given(trialBalanceQueryPort.sumByAccount(FROM, to))
                .willReturn(List.of(new TrialBalanceLine(
                        "20100", "예수금", GlAccountClass.LIABILITY, GlNormalBalance.CREDIT, 1_000L, 1_000L, 2)));

        TrialBalance result = service.query(new TrialBalanceQuery(FROM, to));

        assertThat(result.fromDate()).isEqualTo(FROM);
        assertThat(result.toDate()).isEqualTo(to);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.balanced()).isTrue();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 CMN0003, 집계를 돌리지 않는다")
    void rejectsReversedPeriod() {
        assertThatThrownBy(() -> service.query(new TrialBalanceQuery(FROM, FROM.minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE);
        verifyNoInteractions(trialBalanceQueryPort);
    }

    @Test
    @DisplayName("종료일 - 시작일이 365일이면 허용한다 — FE checkPeriodRange 와 같은 경계")
    void allows365DaySpan() {
        given(trialBalanceQueryPort.sumByAccount(any(), any())).willReturn(List.of());

        TrialBalance result = service.query(new TrialBalanceQuery(FROM, FROM.plusDays(365)));

        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("윤일이 끼면 달력상 1년(366일)도 CMN0004 — 연 단위가 아니라 일수로 센다")
    void rejectsCalendarYearSpanningLeapDay() {
        LocalDate from = LocalDate.of(2027, 3, 1);

        assertThatThrownBy(() -> service.query(new TrialBalanceQuery(from, from.plusYears(1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED);
        verifyNoInteractions(trialBalanceQueryPort);
    }

    @Test
    @DisplayName("365일을 하루라도 넘기면 CMN0004, 집계를 돌리지 않는다")
    void rejectsSpanOver365Days() {
        assertThatThrownBy(() -> service.query(new TrialBalanceQuery(FROM, FROM.plusDays(366))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED);
        verifyNoInteractions(trialBalanceQueryPort);
    }

    // 값의 근거는 개선 전 운영 측정(전 구간 p95 27.4초, 최댓값 30.0초). 타임아웃이 실제로 쿼리에 걸리는지는
    // TrialBalanceIndexAndTimeoutTest 가 본다.
    @Test
    @DisplayName("집계 트랜잭션은 읽기 전용이고 30초 타임아웃이 걸린다")
    void queryTransactionIsReadOnlyWithThirtySecondTimeout() throws NoSuchMethodException {
        Transactional transactional = TrialBalanceQueryService.class
                .getMethod("query", TrialBalanceQuery.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.timeout()).isEqualTo(30);
    }
}
