package com.shinhan.corebank.scheduledtransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultPage;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSort;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferExecutionResultAggregate;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
class ScheduledTransferQueryServiceTest {

    @Mock
    ScheduledTransferQueryPort scheduledTransferQueryPort;

    @Mock
    AccountStatusPort accountStatusPort;

    @Mock
    Clock clock;

    @InjectMocks
    ScheduledTransferQueryService scheduledTransferQueryService;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    private void stubClock() {
        when(clock.instant()).thenReturn(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("customerId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void rejectsMissingCustomerId() {
        assertThatThrownBy(() -> scheduledTransferQueryService.search(null, null, null, null, null, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("withdrawalAccountId가 없어도 정상 조회된다 (REQ-SCD-007: 미지정 시 전체 계좌)")
    void allowsMissingWithdrawalAccountId() {
        stubClock();
        when(scheduledTransferQueryPort.search(1L, null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, null, null, 0, 10, false);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("허용되지 않은 size면 CMN0005를 던지고 포트는 호출하지 않는다")
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, null, null, 0, 7, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("page가 음수면 CMN0001을 던지고 포트는 호출하지 않는다")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, null, null, -1, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("시작일만 있고 종료일이 없으면 범위 검증 없이 그대로 조회한다")
    void allowsOnlyFromDate() {
        stubClock();
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        when(scheduledTransferQueryPort.search(1L, null, null, fromDate, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, fromDate, null, 0, 10, false);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 CMN0003을 던진다")
    void rejectsFromDateAfterToDate() {
        LocalDate fromDate = LocalDate.of(2026, 6, 2);
        LocalDate toDate = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("조회기간이 1년을 초과하면 CMN0004를 던진다")
    void rejectsRangeExceeding365Days() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(366);

        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("조회기간이 정확히 365일이면 통과한다 (경계값)")
    void allowsExactly365Days() {
        stubClock();
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(365);
        when(scheduledTransferQueryPort.search(1L, null, null, fromDate, toDate, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10, false);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("검증을 통과하면 포트 결과를 ScheduledTransferListItem으로 매핑하고, "
            + "동일 출금계좌는 한 번만 조회한다 (N+1 방지). fromAlias·myPassbookMemo·registeredAt도 함께 매핑된다")
    void delegatesToPortAndMapsWithBulkAccountLookup() {
        stubClock();
        ScheduledTransfer waiting = ScheduledTransfer.reconstitute(
                101L, 1L, 2L, "088", "110987654321", "홍길동", 300_000L,
                LocalDate.of(2026, 9, 1), "생활비", null, ScheduledTransferStatus.WAITING,
                null, LocalDateTime.of(2026, 8, 1, 10, 0), null, null, null, null);
        ScheduledTransfer success = ScheduledTransfer.reconstitute(
                102L, 1L, 2L, "088", "110111111111", "김철수", 50_000L,
                LocalDate.of(2026, 8, 10), null, null, ScheduledTransferStatus.SUCCESS,
                "TXN1", LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 10, 9, 0), null, null, null);

        when(scheduledTransferQueryPort.search(1L, ScheduledTransferStatus.WAITING, 2L, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(waiting, success)));
        when(accountStatusPort.findAccountNumbersByIds(List.of(2L)))
                .thenReturn(Map.of(2L, "110123456789"));
        when(accountStatusPort.findAccountAliasesByIds(List.of(2L)))
                .thenReturn(Map.of(2L, "우리집"));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(
                1L, ScheduledTransferStatus.WAITING, 2L, null, null, 0, 10, false);

        assertThat(result.getContent()).hasSize(2);
        ScheduledTransferListItem first = result.getContent().get(0);
        assertThat(first.scheduledTransferId()).isEqualTo(101L);
        assertThat(first.withdrawalAccountId()).isEqualTo(2L);
        assertThat(first.withdrawalAccountNumber()).isEqualTo("110123456789");
        assertThat(first.fromAlias()).isEqualTo("우리집");
        assertThat(first.myPassbookMemo()).isEqualTo("생활비");
        assertThat(first.registeredAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
        assertThat(first.cancelable()).isTrue();
        assertThat(result.getContent().get(1).cancelable()).isFalse();

        verify(accountStatusPort, times(1)).findAccountNumbersByIds(List.of(2L));
        verify(accountStatusPort, times(1)).findAccountAliasesByIds(List.of(2L));
    }

    @Test
    @DisplayName("출금계좌에 별칭이 설정돼 있지 않으면 fromAlias는 null로 매핑된다")
    void mapsFromAliasNull_whenAliasNotSet() {
        stubClock();
        ScheduledTransfer waiting = ScheduledTransfer.reconstitute(
                101L, 1L, 2L, "088", "110987654321", "홍길동", 300_000L,
                LocalDate.of(2026, 9, 1), null, null, ScheduledTransferStatus.WAITING,
                null, LocalDateTime.of(2026, 8, 1, 10, 0), null, null, null, null);

        when(scheduledTransferQueryPort.search(1L, null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(waiting)));
        when(accountStatusPort.findAccountNumbersByIds(List.of(2L)))
                .thenReturn(Map.of(2L, "110123456789"));
        when(accountStatusPort.findAccountAliasesByIds(List.of(2L)))
                .thenReturn(Map.of());

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(
                1L, null, null, null, null, 0, 10, false);

        assertThat(result.getContent().get(0).fromAlias()).isNull();
    }

    @Test
    @DisplayName("WAITING이어도 실행 예정일이 오늘이면 cancelable은 false다 (SCD0303과 동일 기준, PR #227 리뷰)")
    void cancelableIsFalse_whenWaitingButScheduledForToday() {
        stubClock();
        ScheduledTransfer waitingToday = ScheduledTransfer.reconstitute(
                101L, 1L, 2L, "088", "110987654321", "홍길동", 300_000L,
                TODAY, null, null, ScheduledTransferStatus.WAITING,
                null, LocalDateTime.of(2026, 8, 1, 10, 0), null, null, null, null);

        when(scheduledTransferQueryPort.search(1L, null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(waitingToday)));
        when(accountStatusPort.findAccountNumbersByIds(List.of(2L)))
                .thenReturn(Map.of(2L, "110123456789"));
        when(accountStatusPort.findAccountAliasesByIds(List.of(2L)))
                .thenReturn(Map.of());

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(
                1L, null, null, null, null, 0, 10, false);

        assertThat(result.getContent().get(0).cancelable()).isFalse();
    }

    @Test
    @DisplayName("all=true면 size가 허용값이 아니어도 예외 없이 unpaged로 조회한다")
    void allTrue_skipsPageSizeValidation_usesUnpaged() {
        stubClock();
        when(scheduledTransferQueryPort.search(1L, null, null, null, null, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, null, null, 0, 7, true);

        assertThat(result.getContent()).isEmpty();
        verify(scheduledTransferQueryPort).search(1L, null, null, null, null, Pageable.unpaged());
    }

    @Test
    @DisplayName("all=true인데 결과가 100건을 초과하면 CMN0006을 던진다")
    void allTrue_exceedsMaxAllQuerySize_throwsAllQueryTooLarge() {
        when(scheduledTransferQueryPort.search(1L, null, null, null, null, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 101));

        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, null, null, 0, 7, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.ALL_QUERY_TOO_LARGE));
    }

    @Test
    @DisplayName("all=true이고 결과가 정확히 100건이면 통과한다 (경계값)")
    void allTrue_exactlyMaxAllQuerySize_succeeds() {
        stubClock();
        when(scheduledTransferQueryPort.search(1L, null, null, null, null, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 100));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, null, null, 0, 7, true);

        assertThat(result.getTotalElements()).isEqualTo(100);
    }

    private void verifyPortNeverCalled() {
        org.mockito.Mockito.verify(scheduledTransferQueryPort, never())
                .search(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("처리결과 조회: customerId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void searchExecutionResults_rejectsMissingCustomerId() {
        assertThatThrownBy(() -> scheduledTransferQueryService.searchExecutionResults(null, null, null, null, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verify(scheduledTransferQueryPort, never())
                .searchExecutionResults(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("처리결과 조회: 허용되지 않은 size면 CMN0005를 던진다")
    void searchExecutionResults_rejectsInvalidPageSize() {
        assertThatThrownBy(() -> scheduledTransferQueryService.searchExecutionResults(1L, null, null, null, ScheduledTransferExecutionResultSort.LATEST, 0, 7, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));
    }

    @Test
    @DisplayName("처리결과 조회: page가 음수면 CMN0001을 던진다")
    void searchExecutionResults_rejectsNegativePage() {
        assertThatThrownBy(() -> scheduledTransferQueryService.searchExecutionResults(1L, null, null, null, ScheduledTransferExecutionResultSort.LATEST, -1, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("처리결과 조회: all=true면 size가 허용값이 아니어도 예외 없이 unpaged로 조회한다")
    void searchExecutionResults_allTrue_skipsPageSizeValidation_usesUnpaged() {
        stubClock();
        LocalDate expectedFrom = TODAY.minusMonths(1);
        when(scheduledTransferQueryPort.searchExecutionResults(1L, null, expectedFrom, TODAY, ScheduledTransferExecutionResultSort.LATEST, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));
        when(scheduledTransferQueryPort.summarizeExecutionResults(1L, null, expectedFrom, TODAY))
                .thenReturn(ScheduledTransferExecutionResultAggregate.empty());

        ScheduledTransferExecutionResultPage result = scheduledTransferQueryService.searchExecutionResults(
                1L, null, null, null, ScheduledTransferExecutionResultSort.LATEST, 0, 7, true);

        assertThat(result.page().getContent()).isEmpty();
        verify(scheduledTransferQueryPort).searchExecutionResults(1L, null, expectedFrom, TODAY, ScheduledTransferExecutionResultSort.LATEST, Pageable.unpaged());
    }

    @Test
    @DisplayName("처리결과 조회: fromDate/toDate 둘 다 없으면 기본값(오늘-1개월 ~ 오늘)이 적용된다")
    void searchExecutionResults_appliesDefaultOneMonthPeriod() {
        stubClock();
        LocalDate expectedFrom = TODAY.minusMonths(1);
        when(scheduledTransferQueryPort.searchExecutionResults(1L, null, expectedFrom, TODAY, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));
        when(scheduledTransferQueryPort.summarizeExecutionResults(1L, null, expectedFrom, TODAY))
                .thenReturn(ScheduledTransferExecutionResultAggregate.empty());

        ScheduledTransferExecutionResultPage result =
                scheduledTransferQueryService.searchExecutionResults(1L, null, null, null, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false);

        assertThat(result.page().getContent()).isEmpty();
        verify(scheduledTransferQueryPort).searchExecutionResults(1L, null, expectedFrom, TODAY, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("처리결과 조회: toDate만 있으면 fromDate는 toDate-1개월로 채워진다")
    void searchExecutionResults_onlyToDate_fromDateDefaultsToOneMonthBefore() {
        stubClock();
        LocalDate toDate = LocalDate.of(2026, 6, 15);
        LocalDate expectedFrom = LocalDate.of(2026, 5, 15);
        when(scheduledTransferQueryPort.searchExecutionResults(1L, null, expectedFrom, toDate, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));
        when(scheduledTransferQueryPort.summarizeExecutionResults(1L, null, expectedFrom, toDate))
                .thenReturn(ScheduledTransferExecutionResultAggregate.empty());

        scheduledTransferQueryService.searchExecutionResults(1L, null, null, toDate, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false);

        verify(scheduledTransferQueryPort).searchExecutionResults(1L, null, expectedFrom, toDate, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("처리결과 조회: fromDate만 있으면 toDate는 오늘로 채워진다")
    void searchExecutionResults_onlyFromDate_toDateDefaultsToToday() {
        stubClock();
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        when(scheduledTransferQueryPort.searchExecutionResults(1L, null, fromDate, TODAY, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));
        when(scheduledTransferQueryPort.summarizeExecutionResults(1L, null, fromDate, TODAY))
                .thenReturn(ScheduledTransferExecutionResultAggregate.empty());

        scheduledTransferQueryService.searchExecutionResults(1L, null, fromDate, null, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false);

        verify(scheduledTransferQueryPort).searchExecutionResults(1L, null, fromDate, TODAY, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("처리결과 조회: 시작일이 종료일보다 늦으면 CMN0003을 던진다")
    void searchExecutionResults_rejectsFromDateAfterToDate() {
        stubClock();
        LocalDate fromDate = LocalDate.of(2026, 6, 2);
        LocalDate toDate = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> scheduledTransferQueryService.searchExecutionResults(1L, null, fromDate, toDate, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    @DisplayName("처리결과 조회: 조회기간이 1년을 초과하면 CMN0004를 던진다")
    void searchExecutionResults_rejectsRangeExceeding365Days() {
        stubClock();
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(366);

        assertThatThrownBy(() -> scheduledTransferQueryService.searchExecutionResults(1L, null, fromDate, toDate, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED));
    }

    @Test
    @DisplayName("처리결과 조회: 정상 조회 시 항목은 출금계좌번호가 채워져 매핑되고, 집계는 그대로 전달된다")
    void searchExecutionResults_mapsItemsAndSummary() {
        stubClock();
        ScheduledTransfer success = ScheduledTransfer.reconstitute(
                201L, 1L, 2L, "088", "110987654321", "홍길동", 100_000L,
                LocalDate.of(2026, 8, 10), null, null, ScheduledTransferStatus.SUCCESS,
                "TXN100", LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 10, 9, 0), null, null, null);
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 15);
        when(scheduledTransferQueryPort.searchExecutionResults(1L, 2L, fromDate, toDate, ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(success)));
        when(accountStatusPort.findAccountNumbersByIds(List.of(2L)))
                .thenReturn(Map.of(2L, "110123456789"));
        ScheduledTransferExecutionResultAggregate aggregate =
                new ScheduledTransferExecutionResultAggregate(1L, 100_000L, 2L, 30_000L, 1L, 50_000L);
        when(scheduledTransferQueryPort.summarizeExecutionResults(1L, 2L, fromDate, toDate))
                .thenReturn(aggregate);

        ScheduledTransferExecutionResultPage result =
                scheduledTransferQueryService.searchExecutionResults(1L, 2L, fromDate, toDate, ScheduledTransferExecutionResultSort.LATEST, 0, 10, false);

        assertThat(result.page().getContent()).hasSize(1);
        ScheduledTransferExecutionResultItem item = result.page().getContent().get(0);
        assertThat(item.scheduledTransferId()).isEqualTo(201L);
        assertThat(item.withdrawalAccountNumber()).isEqualTo("110123456789");
        assertThat(item.transactionNumber()).isEqualTo("TXN100");

        assertThat(result.summary().successCount()).isEqualTo(1L);
        assertThat(result.summary().successAmount()).isEqualTo(100_000L);
        assertThat(result.summary().failedCount()).isEqualTo(2L);
        assertThat(result.summary().failedAmount()).isEqualTo(30_000L);
        assertThat(result.summary().canceledCount()).isEqualTo(1L);
        assertThat(result.summary().canceledAmount()).isEqualTo(50_000L);
    }
}
