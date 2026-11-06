package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.LedgerAccumulatedBalancePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerAccumulatedBalanceQueryServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 4);

    @Mock
    private LedgerAccumulatedBalancePort port;

    @Test
    void calculate_delegatesToPersistencePort() {
        var service = new LedgerAccumulatedBalanceQueryService(port);
        List<Long> ids = List.of(101L, 102L);
        Map<Long, Long> expected = Map.of(101L, 1200L, 102L, 0L);

        when(port.calculate(ids, FROM, TO)).thenReturn(expected);

        assertThat(service.calculate(ids, FROM, TO)).isEqualTo(expected);

        verify(port).calculate(ids, FROM, TO);
    }

    @Test
    void calculate_rejectsMissingFields() {
        var service = new LedgerAccumulatedBalanceQueryService(port);

        assertMissing(() -> service.calculate(null, FROM, TO));
        assertMissing(() -> service.calculate(List.of(101L), null, TO));
        assertMissing(() -> service.calculate(List.of(101L), FROM, null));
        assertMissing(() -> service.calculate(java.util.Arrays.asList(101L, null), FROM, TO));

        verifyNoInteractions(port);
    }

    @Test
    void calculate_rejectsInvalidAccountIds() {
        var service = new LedgerAccumulatedBalanceQueryService(port);

        for (long id : new long[] {0L, -1L}) {
            assertThatThrownBy(() -> service.calculate(List.of(id), FROM, TO))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                            .isEqualTo(CommonErrorCode.INVALID_INPUT));
        }

        verifyNoInteractions(port);
    }

    @Test
    void calculate_rejectsReversedDateRange() {
        var service = new LedgerAccumulatedBalanceQueryService(port);

        assertThatThrownBy(() -> service.calculate(List.of(101L), TO, FROM))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_DATE_RANGE));

        verifyNoInteractions(port);
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class).satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }
}
