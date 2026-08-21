package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryDirection;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountTransactionQueryServiceTest {

    @Mock
    private AccountPersistencePort
            accountPersistencePort;

    @Mock
    private LedgerHistoryQueryUseCase
            ledgerHistoryQueryUseCase;

    private AccountTransactionQueryService service;

    private final Clock clock =
            Clock.fixed(
                    Instant.parse(
                            "2026-08-20T03:00:00Z"
                    ),
                    ZoneId.of("Asia/Seoul")
            );

    @BeforeEach
    void setUp() {
        service =
                new AccountTransactionQueryService(
                        accountPersistencePort,
                        ledgerHistoryQueryUseCase,
                        clock
                );
    }

    @Test
    void defaultCondition_isNormalizedAndPageConvertedToZeroBased() {
        Account ownedAccount =
                mock(Account.class);

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(
                Optional.of(ownedAccount)
        );

        LedgerHistoryResult ledgerResult =
                LedgerHistoryResult.builder()
                        .page(0)
                        .size(10)
                        .totalCount(0L)
                        .totalPages(0)
                        .items(List.of())
                        .build();

        when(
                ledgerHistoryQueryUseCase
                        .query(any())
        ).thenReturn(ledgerResult);

        service.getTransactions(
                1L,
                101L,
                null,
                null,
                null,
                null,
                null,
                1,
                10
        );

        ArgumentCaptor<LedgerHistoryQuery> captor =
                ArgumentCaptor.forClass(
                        LedgerHistoryQuery.class
                );

        verify(
                ledgerHistoryQueryUseCase
        ).query(
                captor.capture()
        );

        LedgerHistoryQuery query =
                captor.getValue();

        assertThat(query.accountId())
                .isEqualTo(101L);

        assertThat(query.fromDate())
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                7,
                                20
                        )
                );

        assertThat(query.toDate())
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                8,
                                20
                        )
                );

        assertThat(query.direction())
                .isEqualTo(
                        LedgerHistoryDirection.ALL
                );

        assertThat(query.sort())
                .isEqualTo(
                        LedgerHistorySort.LATEST
                );

        assertThat(query.page())
                .isZero();

        assertThat(query.size())
                .isEqualTo(10);
    }

    @Test
    void accountNotOwned_throwsAcc0201() {
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(Optional.empty());

        BusinessException exception =
                org.assertj.core.api.Assertions
                        .catchThrowableOfType(
                                () ->
                                        service.getTransactions(
                                                1L,
                                                101L,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                1,
                                                10
                                        ),
                                BusinessException.class
                        );

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                AccountErrorCode
                        .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
        );

        verifyNoInteractions(
                ledgerHistoryQueryUseCase
        );
    }

    @Test
    void fromDateAfterToDate_throwsCmn0003() {
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(
                Optional.of(
                        mock(Account.class)
                )
        );

        BusinessException exception =
                org.assertj.core.api.Assertions
                        .catchThrowableOfType(
                                () ->
                                        service.getTransactions(
                                                1L,
                                                101L,
                                                LocalDate.of(
                                                        2026,
                                                        8,
                                                        21
                                                ),
                                                LocalDate.of(
                                                        2026,
                                                        8,
                                                        20
                                                ),
                                                LedgerHistoryDirection.ALL,
                                                null,
                                                LedgerHistorySort.LATEST,
                                                1,
                                                10
                                        ),
                                BusinessException.class
                        );

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                CommonErrorCode.INVALID_DATE_RANGE
        );

        verifyNoInteractions(
                ledgerHistoryQueryUseCase
        );
    }

    @Test
    void dateRangeOverOneYear_throwsCmn0004() {
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(
                Optional.of(
                        mock(Account.class)
                )
        );

        BusinessException exception =
                org.assertj.core.api.Assertions
                        .catchThrowableOfType(
                                () ->
                                        service.getTransactions(
                                                1L,
                                                101L,
                                                LocalDate.of(
                                                        2025,
                                                        8,
                                                        19
                                                ),
                                                LocalDate.of(
                                                        2026,
                                                        8,
                                                        20
                                                ),
                                                LedgerHistoryDirection.ALL,
                                                null,
                                                LedgerHistorySort.LATEST,
                                                1,
                                                10
                                        ),
                                BusinessException.class
                        );

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                CommonErrorCode.DATE_RANGE_EXCEEDED
        );
    }

    @Test
    void unsupportedPageSize_throwsCmn0005() {
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(
                Optional.of(
                        mock(Account.class)
                )
        );

        BusinessException exception =
                org.assertj.core.api.Assertions
                        .catchThrowableOfType(
                                () ->
                                        service.getTransactions(
                                                1L,
                                                101L,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                1,
                                                7
                                        ),
                                BusinessException.class
                        );

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                CommonErrorCode.INVALID_PAGE_SIZE
        );
    }

    @Test
    void zeroPage_throwsCmn0001() {
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(
                Optional.of(
                        mock(Account.class)
                )
        );

        BusinessException exception =
                org.assertj.core.api.Assertions
                        .catchThrowableOfType(
                                () ->
                                        service.getTransactions(
                                                1L,
                                                101L,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                0,
                                                10
                                        ),
                                BusinessException.class
                        );

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                CommonErrorCode.INVALID_INPUT
        );
    }

    @Test
    void secondApiPage_isConvertedToInternalPageOne() {
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                101L,
                                1L
                        )
        ).thenReturn(
                Optional.of(
                        mock(Account.class)
                )
        );

        when(
                ledgerHistoryQueryUseCase
                        .query(any())
        ).thenReturn(
                mock(LedgerHistoryResult.class)
        );

        service.getTransactions(
                1L,
                101L,
                LocalDate.of(
                        2026,
                        8,
                        1
                ),
                LocalDate.of(
                        2026,
                        8,
                        20
                ),
                LedgerHistoryDirection.ALL,
                null,
                LedgerHistorySort.LATEST,
                2,
                10
        );

        ArgumentCaptor<LedgerHistoryQuery> captor =
                ArgumentCaptor.forClass(
                        LedgerHistoryQuery.class
                );

        verify(
                ledgerHistoryQueryUseCase
        ).query(
                captor.capture()
        );

        assertThat(
                captor.getValue().page()
        ).isEqualTo(1);
    }
}