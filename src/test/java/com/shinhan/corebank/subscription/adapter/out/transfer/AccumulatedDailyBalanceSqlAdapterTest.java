package com.shinhan.corebank.subscription.adapter.out.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.api.LedgerAccumulatedBalanceQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccumulatedDailyBalanceSqlAdapterTest {

    @Mock
    private LedgerAccumulatedBalanceQuery query;

    @Test
    void calculate_delegatesToTransferApi() {
        var adapter = new AccumulatedDailyBalanceSqlAdapter(query);

        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 4);

        List<Long> ids = List.of(101L, 102L);
        Map<Long, Long> expected = Map.of(101L, 1200L, 102L, 0L);

        when(query.calculate(ids, from, to)).thenReturn(expected);

        assertThat(adapter.calculate(ids, from, to)).isEqualTo(expected);

        verify(query).calculate(ids, from, to);
    }
}
