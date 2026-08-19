package com.shinhan.corebank.scheduledtransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.LocalDate;
import java.util.List;
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
        Page<ScheduledTransfer> expected = new PageImpl<>(List.of());
        when(scheduledTransferQueryPort.search(1L, null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(expected);

        Page<ScheduledTransfer> result = scheduledTransferQueryService.search(1L, null, null, null, null, 0, 10);

        assertThat(result).isSameAs(expected);
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
        Page<ScheduledTransfer> expected = new PageImpl<>(List.of());
        when(scheduledTransferQueryPort.search(1L, null, null, fromDate, null, PageRequest.of(0, 10)))
                .thenReturn(expected);

        Page<ScheduledTransfer> result = scheduledTransferQueryService.search(1L, null, null, fromDate, null, 0, 10);

        assertThat(result).isSameAs(expected);
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
        Page<ScheduledTransfer> expected = new PageImpl<>(List.of());
        when(scheduledTransferQueryPort.search(1L, null, null, fromDate, toDate, PageRequest.of(0, 10)))
                .thenReturn(expected);

        Page<ScheduledTransfer> result = scheduledTransferQueryService.search(1L, null, null, fromDate, toDate, 0, 10);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("검증을 통과하면 포트 결과를 그대로 반환한다")
    void delegatesToPort() {
        Page<ScheduledTransfer> expected = new PageImpl<>(List.of());
        when(scheduledTransferQueryPort.search(1L, ScheduledTransferStatus.WAITING, 2L, null, null, PageRequest.of(0, 10)))
                .thenReturn(expected);

        Page<ScheduledTransfer> result = scheduledTransferQueryService.search(
                1L, ScheduledTransferStatus.WAITING, 2L, null, null, 0, 10);

        assertThat(result).isSameAs(expected);
    }

    private void verifyPortNeverCalled() {
        org.mockito.Mockito.verify(scheduledTransferQueryPort, never())
                .search(any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
