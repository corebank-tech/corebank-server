package com.shinhan.corebank.scheduledtransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @InjectMocks
    ScheduledTransferQueryService scheduledTransferQueryService;

    @Test
    @DisplayName("customerId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void rejectsMissingCustomerId() {
        assertThatThrownBy(() -> scheduledTransferQueryService.search(null, null, null, null, null, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("withdrawalAccountId가 없어도 정상 조회된다 (REQ-SCD-007: 미지정 시 전체 계좌)")
    void allowsMissingWithdrawalAccountId() {
        when(scheduledTransferQueryPort.search(1L, null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, null, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("허용되지 않은 size면 CMN0005를 던지고 포트는 호출하지 않는다")
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, null, null, 0, 7))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("page가 음수면 CMN0001을 던지고 포트는 호출하지 않는다")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, null, null, -1, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("시작일만 있고 종료일이 없으면 범위 검증 없이 그대로 조회한다")
    void allowsOnlyFromDate() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        when(scheduledTransferQueryPort.search(1L, null, null, fromDate, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, fromDate, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 CMN0003을 던진다")
    void rejectsFromDateAfterToDate() {
        LocalDate fromDate = LocalDate.of(2026, 6, 2);
        LocalDate toDate = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10))
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

        assertThatThrownBy(() -> scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED));

        verifyPortNeverCalled();
    }

    @Test
    @DisplayName("조회기간이 정확히 365일이면 통과한다 (경계값)")
    void allowsExactly365Days() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(365);
        when(scheduledTransferQueryPort.search(1L, null, null, fromDate, toDate, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("검증을 통과하면 포트 결과를 ScheduledTransferListItem으로 매핑하고, "
            + "동일 출금계좌는 한 번만 조회한다 (N+1 방지)")
    void delegatesToPortAndMapsWithBulkAccountLookup() {
        ScheduledTransfer waiting = ScheduledTransfer.reconstitute(
                101L, 1L, 2L, "088", "110987654321", "홍길동", 300_000L,
                LocalDate.of(2026, 9, 1), null, null, ScheduledTransferStatus.WAITING,
                null, LocalDateTime.of(2026, 8, 1, 10, 0), null, null, null);
        ScheduledTransfer success = ScheduledTransfer.reconstitute(
                102L, 1L, 2L, "088", "110111111111", "김철수", 50_000L,
                LocalDate.of(2026, 8, 10), null, null, ScheduledTransferStatus.SUCCESS,
                "TXN1", LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 10, 9, 0), null, null);

        when(scheduledTransferQueryPort.search(1L, ScheduledTransferStatus.WAITING, 2L, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(waiting, success)));
        when(accountStatusPort.findAccountNumbersByIds(List.of(2L)))
                .thenReturn(Map.of(2L, "110123456789"));

        Page<ScheduledTransferListItem> result = scheduledTransferQueryService.search(
                1L, ScheduledTransferStatus.WAITING, 2L, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(2);
        ScheduledTransferListItem first = result.getContent().get(0);
        assertThat(first.scheduledTransferId()).isEqualTo(101L);
        assertThat(first.withdrawalAccountNumber()).isEqualTo("110123456789");
        assertThat(first.cancelable()).isTrue();
        assertThat(result.getContent().get(1).cancelable()).isFalse();

        verify(accountStatusPort, times(1)).findAccountNumbersByIds(List.of(2L));
    }

    private void verifyPortNeverCalled() {
        org.mockito.Mockito.verify(scheduledTransferQueryPort, never())
                .search(any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
