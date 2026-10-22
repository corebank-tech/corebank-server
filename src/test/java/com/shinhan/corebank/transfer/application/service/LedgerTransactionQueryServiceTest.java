package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.LedgerTransactionSummary;
import com.shinhan.corebank.transfer.application.port.out.LedgerTransactionQueryPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerTransactionQueryService 단위 테스트")
class LedgerTransactionQueryServiceTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 5);

    @Mock
    private LedgerTransactionQueryPort port;

    @Mock
    private BusinessDateProvider businessDateProvider;

    @InjectMocks
    private LedgerTransactionQueryService service;

    @Test
    @DisplayName("직전 날이 영업일이면 발생 시각과 거래번호 범위를 거래일부터 2일 뒤 전까지로 좁혀 포트에 넘긴다")
    void narrowsRangeToTradeDateAfterBusinessDay() {
        // 2026-10-08(목). 직전 날 10/7(수)은 영업일이다.
        LocalDate thursday = LocalDate.of(2026, 10, 8);
        given(businessDateProvider.isBusinessDay(LocalDate.of(2026, 10, 7))).willReturn(true);
        List<LedgerTransactionSummary> rows =
                List.of(new LedgerTransactionSummary("20261008WB0000000001", "IMMEDIATE_TRANSFER", thursday, 1_000L));
        given(port.findByTradeDate(
                        thursday,
                        LocalDateTime.of(2026, 10, 8, 0, 0),
                        LocalDateTime.of(2026, 10, 10, 0, 0),
                        "20261008",
                        "20261010",
                        "",
                        100))
                .willReturn(rows);

        assertThat(service.findByTradeDate(thursday, null, 100)).isEqualTo(rows);
    }

    @Test
    @DisplayName("연휴 뒤 거래일이면 직전 영업일 다음 날부터 읽어 연휴 중 발생한 거래를 포함한다")
    void startsAfterPreviousBusinessDayAcrossHolidays() {
        // TRADE_DATE 2026-10-05(월). 10/3(토, 개천절)과 10/4(일)은 휴일, 10/2(금)은 영업일이다.
        given(businessDateProvider.isBusinessDay(LocalDate.of(2026, 10, 4))).willReturn(false);
        given(businessDateProvider.isBusinessDay(LocalDate.of(2026, 10, 3))).willReturn(false);
        given(businessDateProvider.isBusinessDay(LocalDate.of(2026, 10, 2))).willReturn(true);
        given(port.findByTradeDate(
                        TRADE_DATE,
                        LocalDateTime.of(2026, 10, 3, 0, 0),
                        LocalDateTime.of(2026, 10, 7, 0, 0),
                        "20261003",
                        "20261007",
                        "",
                        100))
                .willReturn(List.of());

        assertThat(service.findByTradeDate(TRADE_DATE, null, 100)).isEmpty();
    }

    @Test
    @DisplayName("영업일을 찾지 못해도 14일 넘게 거슬러 가지 않는다")
    void capsLookback() {
        given(businessDateProvider.isBusinessDay(org.mockito.ArgumentMatchers.any()))
                .willReturn(false);
        given(port.findByTradeDate(
                        eq(TRADE_DATE),
                        eq(LocalDateTime.of(2026, 9, 21, 0, 0)),
                        eq(LocalDateTime.of(2026, 10, 7, 0, 0)),
                        eq("20260921"),
                        eq("20261007"),
                        eq(""),
                        anyInt()))
                .willReturn(List.of());

        assertThat(service.findByTradeDate(TRADE_DATE, null, 10)).isEmpty();
    }

    @Test
    @DisplayName("커서가 있으면 그대로 넘긴다")
    void passesCursor() {
        given(businessDateProvider.isBusinessDay(TRADE_DATE.minusDays(1))).willReturn(true);
        given(port.findByTradeDate(
                        eq(TRADE_DATE),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        anyString(),
                        anyString(),
                        eq("20261005WB0000000001"),
                        anyInt()))
                .willReturn(List.of());

        assertThat(service.findByTradeDate(TRADE_DATE, "20261005WB0000000001", 10))
                .isEmpty();
    }

    @Test
    @DisplayName("거래일이 없으면 CMN0002, limit 이 1 미만이면 CMN0001")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> service.findByTradeDate(null, null, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
        assertThatThrownBy(() -> service.findByTradeDate(TRADE_DATE, null, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(port);
    }
}
