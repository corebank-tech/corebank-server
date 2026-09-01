package com.shinhan.corebank.autotransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryResult;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryAggregate;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryQueryPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryRow;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AutoTransferExecutionHistoryQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Mock
    AutoTransferExecutionHistoryQueryPort autoTransferExecutionHistoryQueryPort;

    @Mock
    Clock clock;

    @InjectMocks
    AutoTransferExecutionHistoryQueryService service;

    // service.search()는 fromDate/toDate를 직접 줘도 "오늘"을 항상 먼저 계산하므로(49번째 줄),
    // 모든 테스트에서 clock.withZone(SEOUL)이 호출된다 — 매 테스트마다 고정해둔다.
    @BeforeEach
    void fixToday() {
        Clock fixed = Clock.fixed(TODAY.atStartOfDay(SEOUL).toInstant(), SEOUL);
        // 검증 실패로 일찍 return하는 테스트들은 이 스텁을 안 타서 "불필요한 스텁"으로 걸리므로 lenient 처리
        lenient().when(clock.withZone(SEOUL)).thenReturn(fixed);
    }

    private AutoTransferExecutionHistoryRow sampleRow() {
        return new AutoTransferExecutionHistoryRow(
                1L,
                ProcessResultStatus.SUCCESS,
                LocalDateTime.of(2026, 3, 10, 9, 0),
                2L,
                "110987654321",
                "홍길동",
                10_000L,
                1,
                "내메모",
                null);
    }

    @Test
    @DisplayName("fromDate/toDate 둘 다 없으면 오늘부터 1개월 전까지 기본 조회기간이 적용된다")
    void search_defaultsToOneMonthPeriod_whenDatesNotProvided() {
        Pageable pageable = PageRequest.of(0, 10);
        when(autoTransferExecutionHistoryQueryPort.search(eq(1L), eq(2L), eq(TODAY.minusMonths(1)), eq(TODAY), any()))
                .thenReturn(new PageImpl<>(List.of(sampleRow()), pageable, 1));
        when(autoTransferExecutionHistoryQueryPort.summarize(eq(1L), eq(2L), eq(TODAY.minusMonths(1)), eq(TODAY)))
                .thenReturn(new AutoTransferExecutionHistoryAggregate(1L, 10_000L, 0L, 0L));

        AutoTransferExecutionHistoryResult result = service.search(1L, 2L, null, null, 0, 10, false);

        assertThat(result.page().getContent()).hasSize(1);
        assertThat(result.page().getTotalElements()).isEqualTo(1);
        assertThat(result.summary().successCount()).isEqualTo(1L);
        assertThat(result.summary().successAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("toDate만 없으면 fromDate 기준이 아니라 toDate(오늘) 기준으로 1개월 전이 계산된다")
    void search_defaultsFromDate_relativeToToday_whenOnlyToDateMissing() {
        Pageable pageable = PageRequest.of(0, 10);
        when(autoTransferExecutionHistoryQueryPort.search(eq(1L), eq(2L), eq(TODAY.minusMonths(1)), eq(TODAY), any()))
                .thenReturn(Page.empty(pageable));
        when(autoTransferExecutionHistoryQueryPort.summarize(eq(1L), eq(2L), eq(TODAY.minusMonths(1)), eq(TODAY)))
                .thenReturn(AutoTransferExecutionHistoryAggregate.empty());

        service.search(1L, 2L, null, null, 0, 10, false);

        // Mockito eq()가 위 stub에서 이미 정확한 날짜 조합만 매칭시키므로, 예외 없이 통과하면 기본값 계산이 맞다는 뜻
    }

    @Test
    @DisplayName("fromDate/toDate를 둘 다 주면 기본값 계산 없이 그대로 사용한다")
    void search_usesProvidedDates_withoutDefaulting() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = LocalDate.of(2026, 1, 31);
        Pageable pageable = PageRequest.of(0, 10);
        when(autoTransferExecutionHistoryQueryPort.search(eq(1L), eq(2L), eq(fromDate), eq(toDate), any()))
                .thenReturn(Page.empty(pageable));
        when(autoTransferExecutionHistoryQueryPort.summarize(eq(1L), eq(2L), eq(fromDate), eq(toDate)))
                .thenReturn(AutoTransferExecutionHistoryAggregate.empty());

        service.search(1L, 2L, fromDate, toDate, 0, 10, false);
    }

    @Test
    @DisplayName("조회 시작일이 종료일보다 늦으면 INVALID_DATE_RANGE를 던진다")
    void search_fromDateAfterToDate_throwsInvalidDateRange() {
        assertThatThrownBy(() ->
                        service.search(1L, 2L, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 10), 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    @DisplayName("조회기간이 365일을 초과하면 DATE_RANGE_EXCEEDED를 던진다")
    void search_rangeExceeds365Days_throwsDateRangeExceeded() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(366);

        assertThatThrownBy(() -> service.search(1L, 2L, fromDate, toDate, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED));
    }

    @Test
    @DisplayName("조회기간이 정확히 365일이면 통과한다 (경계값)")
    void search_rangeExactly365Days_succeeds() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(365);
        Pageable pageable = PageRequest.of(0, 10);
        when(autoTransferExecutionHistoryQueryPort.search(eq(1L), eq(2L), eq(fromDate), eq(toDate), any()))
                .thenReturn(Page.empty(pageable));
        when(autoTransferExecutionHistoryQueryPort.summarize(eq(1L), eq(2L), eq(fromDate), eq(toDate)))
                .thenReturn(AutoTransferExecutionHistoryAggregate.empty());

        service.search(1L, 2L, fromDate, toDate, 0, 10, false);
    }

    @Test
    @DisplayName("customerId가 없으면 REQUIRED_FIELD_MISSING을 던진다")
    void search_missingCustomerId_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> service.search(null, 2L, null, null, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.REQUIRED_FIELD_MISSING.getMessage());
    }

    @Test
    @DisplayName("withdrawalAccountId가 없으면 REQUIRED_FIELD_MISSING을 던진다")
    void search_missingWithdrawalAccountId_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> service.search(1L, null, null, null, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.REQUIRED_FIELD_MISSING.getMessage());
    }

    @Test
    @DisplayName("허용되지 않은 page size는 INVALID_PAGE_SIZE를 던진다")
    void search_disallowedPageSize_throwsInvalidPageSize() {
        assertThatThrownBy(() -> service.search(1L, 2L, null, null, 0, 7, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.INVALID_PAGE_SIZE.getMessage());
    }

    @Test
    @DisplayName("all=true면 size가 허용값이 아니어도 예외 없이 unpaged로 조회한다")
    void search_allTrue_skipsPageSizeValidation_usesUnpaged() {
        when(autoTransferExecutionHistoryQueryPort.search(
                        eq(1L), eq(2L), eq(TODAY.minusMonths(1)), eq(TODAY), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(sampleRow()), Pageable.unpaged(), 1));
        when(autoTransferExecutionHistoryQueryPort.summarize(eq(1L), eq(2L), eq(TODAY.minusMonths(1)), eq(TODAY)))
                .thenReturn(new AutoTransferExecutionHistoryAggregate(1L, 10_000L, 0L, 0L));

        AutoTransferExecutionHistoryResult result = service.search(1L, 2L, null, null, 0, 7, true);

        assertThat(result.page().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("page가 음수면 INVALID_INPUT을 던진다")
    void search_negativePage_throwsInvalidInput() {
        assertThatThrownBy(() -> service.search(1L, 2L, null, null, -1, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("Row/Aggregate의 값이 Item/Summary로 정확히 매핑된다")
    void search_mapsRowAndAggregateFieldsCorrectly() {
        Pageable pageable = PageRequest.of(0, 10);
        AutoTransferExecutionHistoryRow row = sampleRow();
        when(autoTransferExecutionHistoryQueryPort.search(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
        when(autoTransferExecutionHistoryQueryPort.summarize(eq(1L), eq(2L), any(), any()))
                .thenReturn(new AutoTransferExecutionHistoryAggregate(3L, 30_000L, 1L, 5_000L));

        AutoTransferExecutionHistoryResult result =
                service.search(1L, 2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 0, 10, false);

        var item = result.page().getContent().get(0);
        assertThat(item.executionId()).isEqualTo(row.executionId());
        assertThat(item.status()).isEqualTo(row.status());
        assertThat(item.executedAt()).isEqualTo(row.executedAt());
        assertThat(item.withdrawalAccountId()).isEqualTo(row.withdrawalAccountId());
        assertThat(item.depositAccountNumber()).isEqualTo(row.depositAccountNumber());
        assertThat(item.payeeName()).isEqualTo(row.payeeName());
        assertThat(item.amount()).isEqualTo(row.amount());
        assertThat(item.cycleMonths()).isEqualTo(row.cycleMonths());
        assertThat(item.myPassbookMemo()).isEqualTo(row.myPassbookMemo());
        assertThat(item.failureReason()).isEqualTo(row.failureReason());

        assertThat(result.summary().successCount()).isEqualTo(3L);
        assertThat(result.summary().successAmount()).isEqualTo(30_000L);
        assertThat(result.summary().errorCount()).isEqualTo(1L);
        assertThat(result.summary().errorAmount()).isEqualTo(5_000L);
    }
}
