package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchList;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchView;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationMismatchPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("대사 불일치 목록 조회 서비스")
class LedgerReconciliationMismatchQueryServiceTest {

    private static final LocalDate REQUESTED_DATE = LocalDate.of(2026, 10, 13);
    private static final LocalDate LATEST_DATE = LocalDate.of(2026, 10, 14);
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 10);
    private static final LedgerReconciliationMismatchView VIEW =
            new LedgerReconciliationMismatchView(1L, 1_000L, 900L, LocalDateTime.of(2026, 10, 14, 23, 31));

    @Mock
    LedgerReconciliationMismatchPort mismatchPort;

    private LedgerReconciliationMismatchQueryService service;

    @BeforeEach
    void setUp() {
        service = new LedgerReconciliationMismatchQueryService(mismatchPort);
    }

    @Test
    @DisplayName("영업일을 주면 그날 목록을 돌려준다")
    void find_withDate_returnsThatDate() {
        given(mismatchPort.findByBusinessDate(REQUESTED_DATE, FIRST_PAGE))
                .willReturn(new PageImpl<>(List.of(VIEW), FIRST_PAGE, 1));

        LedgerReconciliationMismatchList result = service.find(REQUESTED_DATE, FIRST_PAGE);

        assertThat(result.businessDate()).isEqualTo(REQUESTED_DATE);
        assertThat(result.mismatches().getContent()).containsExactly(VIEW);
        verify(mismatchPort, never()).findLatestBusinessDate();
    }

    @Test
    @DisplayName("영업일을 빼면 가장 최근 영업일 목록을 돌려준다")
    void find_withoutDate_usesLatestDate() {
        given(mismatchPort.findLatestBusinessDate()).willReturn(Optional.of(LATEST_DATE));
        given(mismatchPort.findByBusinessDate(LATEST_DATE, FIRST_PAGE))
                .willReturn(new PageImpl<>(List.of(VIEW), FIRST_PAGE, 1));

        LedgerReconciliationMismatchList result = service.find(null, FIRST_PAGE);

        assertThat(result.businessDate()).isEqualTo(LATEST_DATE);
        assertThat(result.mismatches().getContent()).containsExactly(VIEW);
    }

    @Test
    @DisplayName("기록이 하나도 없으면 영업일 null과 빈 페이지를 돌려준다")
    void find_withoutDate_noRecords_returnsEmpty() {
        given(mismatchPort.findLatestBusinessDate()).willReturn(Optional.empty());

        LedgerReconciliationMismatchList result = service.find(null, FIRST_PAGE);

        assertThat(result.businessDate()).isNull();
        assertThat(result.mismatches()).isEmpty();
        verify(mismatchPort, never()).findByBusinessDate(any(), any());
    }

    @Test
    @DisplayName("전체 조회는 100건까지 허용한다")
    void find_all_upTo100_allowed() {
        given(mismatchPort.countByBusinessDate(REQUESTED_DATE)).willReturn(100L);
        given(mismatchPort.findByBusinessDate(REQUESTED_DATE, Pageable.unpaged()))
                .willReturn(new PageImpl<>(List.of(VIEW)));

        LedgerReconciliationMismatchList result = service.find(REQUESTED_DATE, Pageable.unpaged());

        assertThat(result.mismatches().getContent()).containsExactly(VIEW);
    }

    @Test
    @DisplayName("전체 조회가 100건을 넘으면 본문을 읽기 전에 CMN0006으로 거부한다")
    void find_all_over100_rejected() {
        given(mismatchPort.countByBusinessDate(REQUESTED_DATE)).willReturn(101L);

        assertThatThrownBy(() -> service.find(REQUESTED_DATE, Pageable.unpaged()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.ALL_QUERY_TOO_LARGE);
        verify(mismatchPort, never()).findByBusinessDate(any(), any());
    }

    @Test
    @DisplayName("차액은 원장 - 잔액이다")
    void view_difference_isLedgerMinusAccount() {
        assertThat(VIEW.difference()).isEqualTo(100L);
        assertThat(new LedgerReconciliationMismatchView(1L, 0L, 50L, null).difference())
                .isEqualTo(-50L);
    }
}
