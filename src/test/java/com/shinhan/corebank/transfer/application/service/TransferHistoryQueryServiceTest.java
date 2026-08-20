package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryDetail;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryPage;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryQueryPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.application.port.out.WithdrawalAccountDetail;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TransferHistoryQueryServiceTest {

    @Mock
    TransferHistoryQueryPort transferHistoryQueryPort;

    @Mock
    TransferLookupPort transferLookupPort;

    @Mock
    AccountLockPort accountLockPort;

    @Mock
    Clock clock;

    @InjectMocks
    TransferHistoryQueryService transferHistoryQueryService;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private static final Long CUSTOMER_ID = 1L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 101L;

    private void stubClock() {
        when(clock.instant()).thenReturn(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    }

    private void stubOwnership(Long customerId, Long withdrawalAccountId) {
        when(accountLockPort.findWithdrawalAccountDetail(withdrawalAccountId))
                .thenReturn(Optional.of(new WithdrawalAccountDetail(withdrawalAccountId, customerId, true)));
    }

    @Test
    @DisplayName("customerId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void search_rejectsMissingCustomerId() {
        assertThatThrownBy(() -> transferHistoryQueryService.search(
                null, WITHDRAWAL_ACCOUNT_ID, null, null, null, TransferHistorySort.LATEST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verifySearchPortNeverCalled();
    }

    @Test
    @DisplayName("withdrawalAccountId가 없으면 CMN0002를 던지고 포트는 호출하지 않는다")
    void search_rejectsMissingWithdrawalAccountId() {
        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, null, null, null, null, TransferHistorySort.LATEST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verifySearchPortNeverCalled();
    }

    @Test
    @DisplayName("허용되지 않은 size면 CMN0005를 던지고 포트는 호출하지 않는다")
    void search_rejectsInvalidPageSize() {
        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, null, null, TransferHistorySort.LATEST, 0, 7))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));

        verifySearchPortNeverCalled();
    }

    @Test
    @DisplayName("page가 음수면 CMN0001을 던지고 포트는 호출하지 않는다")
    void search_rejectsNegativePage() {
        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, null, null, TransferHistorySort.LATEST, -1, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verifySearchPortNeverCalled();
    }

    @Test
    @DisplayName("출금계좌 소유자가 아니면 TRF0001을 던지고 포트는 호출하지 않는다")
    void search_rejectsOwnershipMismatch() {
        when(accountLockPort.findWithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID))
                .thenReturn(Optional.of(new WithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID, 999L, true)));

        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, null, null, TransferHistorySort.LATEST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED));

        verifySearchPortNeverCalled();
    }

    @Test
    @DisplayName("출금계좌 등록이 해지된 계좌라도(withdrawalRegistered=false) 소유자면 조회된다")
    void search_allowsUnregisteredWithdrawalAccount_whenStillOwned() {
        stubClock();
        when(accountLockPort.findWithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID))
                .thenReturn(Optional.of(new WithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID, false)));
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 20);
        when(transferHistoryQueryPort.search(WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        when(transferHistoryQueryPort.summarize(WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate))
                .thenReturn(TransferHistoryAggregate.empty());

        TransferHistoryPage result = transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, 0, 10);

        assertThat(result.page().getContent()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 출금계좌면 TRF0001을 던지고 포트는 호출하지 않는다")
    void search_rejectsUnknownWithdrawalAccount() {
        when(accountLockPort.findWithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, null, null, TransferHistorySort.LATEST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED));

        verifySearchPortNeverCalled();
    }

    @Test
    @DisplayName("fromDate/toDate 둘 다 없으면 기본값(오늘-1개월 ~ 오늘)이 적용된다")
    void search_appliesDefaultOneMonthPeriod() {
        stubClock();
        stubOwnership(CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID);
        LocalDate expectedFrom = TODAY.minusMonths(1);
        when(transferHistoryQueryPort.search(WITHDRAWAL_ACCOUNT_ID, null, expectedFrom, TODAY, TransferHistorySort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        when(transferHistoryQueryPort.summarize(WITHDRAWAL_ACCOUNT_ID, null, expectedFrom, TODAY))
                .thenReturn(TransferHistoryAggregate.empty());

        TransferHistoryPage result = transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, null, null, TransferHistorySort.LATEST, 0, 10);

        assertThat(result.page().getContent()).isEmpty();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 CMN0003을 던진다")
    void search_rejectsFromDateAfterToDate() {
        stubClock();
        stubOwnership(CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID);
        LocalDate fromDate = LocalDate.of(2026, 6, 2);
        LocalDate toDate = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));
    }

    @Test
    @DisplayName("조회기간이 365일을 초과하면 CMN0004를 던진다")
    void search_rejectsRangeExceeding365Days() {
        stubClock();
        stubOwnership(CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID);
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(366);

        assertThatThrownBy(() -> transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DATE_RANGE_EXCEEDED));
    }

    @Test
    @DisplayName("조회기간이 정확히 365일이면 통과한다 (경계값)")
    void search_allowsExactly365Days() {
        stubClock();
        stubOwnership(CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID);
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = fromDate.plusDays(365);
        when(transferHistoryQueryPort.search(WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        when(transferHistoryQueryPort.summarize(WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate))
                .thenReturn(TransferHistoryAggregate.empty());

        TransferHistoryPage result = transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, 0, 10);

        assertThat(result.page().getContent()).isEmpty();
    }

    @Test
    @DisplayName("정상 조회 시 포트 결과를 TransferHistoryItem/Summary로 매핑한다")
    void search_mapsItemsAndSummary() {
        stubClock();
        stubOwnership(CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID);
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 20);

        Transfer transfer = Transfer.create(
                "20260810IT0000000001", WITHDRAWAL_ACCOUNT_ID, 202L, "110222222222", "성춘향",
                10_000L, 0L, TransferType.IMMEDIATE, TransferChannel.BT,
                null, null, null, "출금메모", "입금메모", LocalDateTime.of(2026, 8, 10, 9, 0));
        transfer.complete(90_000L, LocalDateTime.of(2026, 8, 10, 9, 0));

        when(transferHistoryQueryPort.search(WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(transfer)));
        when(transferHistoryQueryPort.summarize(WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate))
                .thenReturn(new TransferHistoryAggregate(1L, 10_000L, 0L, 0L));

        TransferHistoryPage result = transferHistoryQueryService.search(
                CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID, null, fromDate, toDate, TransferHistorySort.LATEST, 0, 10);

        assertThat(result.page().getContent()).hasSize(1);
        assertThat(result.page().getContent().get(0).transactionNumber()).isEqualTo("20260810IT0000000001");
        assertThat(result.page().getContent().get(0).status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.summary().successCount()).isEqualTo(1L);
        assertThat(result.summary().successAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("상세조회: customerId/transactionNumber가 없으면 CMN0002를 던진다")
    void getDetail_rejectsMissingArgs() {
        assertThatThrownBy(() -> transferHistoryQueryService.getDetail(null, "20260810IT0000000001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("상세조회: 거래가 없으면 TRF0202를 던진다")
    void getDetail_rejectsWhenNotFound() {
        when(transferLookupPort.findByTransactionNumber("NOT-EXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferHistoryQueryService.getDetail(CUSTOMER_ID, "NOT-EXIST"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.TRANSACTION_NOT_FOUND));
    }

    @Test
    @DisplayName("상세조회: 타인 소유 거래면 TRF0202를 던진다 (존재 스캐닝 방지를 위해 404로 통일)")
    void getDetail_rejectsWhenNotOwned() {
        Transfer transfer = Transfer.create(
                "20260810IT0000000001", WITHDRAWAL_ACCOUNT_ID, 202L, "110222222222", "성춘향",
                10_000L, 0L, TransferType.IMMEDIATE, TransferChannel.BT,
                null, null, null, "출금메모", "입금메모", LocalDateTime.of(2026, 8, 10, 9, 0));
        transfer.complete(90_000L, LocalDateTime.of(2026, 8, 10, 9, 0));
        when(transferLookupPort.findByTransactionNumber("20260810IT0000000001")).thenReturn(Optional.of(transfer));
        when(accountLockPort.findWithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID))
                .thenReturn(Optional.of(new WithdrawalAccountDetail(WITHDRAWAL_ACCOUNT_ID, 999L, true)));

        assertThatThrownBy(() -> transferHistoryQueryService.getDetail(CUSTOMER_ID, "20260810IT0000000001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.TRANSACTION_NOT_FOUND));
    }

    @Test
    @DisplayName("상세조회: 본인 소유 거래면 상세 정보를 반환한다")
    void getDetail_returnsDetail_whenOwned() {
        Transfer transfer = Transfer.create(
                "20260810IT0000000001", WITHDRAWAL_ACCOUNT_ID, 202L, "110222222222", "성춘향",
                10_000L, 100L, TransferType.IMMEDIATE, TransferChannel.BT,
                null, null, null, "출금메모", "입금메모", LocalDateTime.of(2026, 8, 10, 9, 0));
        transfer.complete(90_000L, LocalDateTime.of(2026, 8, 10, 9, 0));
        when(transferLookupPort.findByTransactionNumber("20260810IT0000000001")).thenReturn(Optional.of(transfer));
        stubOwnership(CUSTOMER_ID, WITHDRAWAL_ACCOUNT_ID);

        TransferHistoryDetail detail = transferHistoryQueryService.getDetail(CUSTOMER_ID, "20260810IT0000000001");

        assertThat(detail.transactionNumber()).isEqualTo("20260810IT0000000001");
        assertThat(detail.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(detail.amount()).isEqualTo(10_000L);
        assertThat(detail.fee()).isEqualTo(100L);
        assertThat(detail.withdrawalBalanceAfter()).isEqualTo(90_000L);
        assertThat(detail.depositAccountNumber()).isEqualTo("110222222222");
        assertThat(detail.payeeName()).isEqualTo("성춘향");
    }

    private void verifySearchPortNeverCalled() {
        org.mockito.Mockito.verify(transferHistoryQueryPort, never())
                .search(any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
