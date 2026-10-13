package com.shinhan.corebank.account.adapter.in.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.in.AvailableBalanceQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AvailableBalanceQueryUseCase.AvailableBalanceSnapshot;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.transfer.api.TransferPreCheckContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailableBalancePreCheckTest {

    private AvailableBalanceQueryUseCase query;
    private AvailableBalancePreCheck preCheck;

    @BeforeEach
    void setUp() {
        query = mock(AvailableBalanceQueryUseCase.class);
        preCheck = new AvailableBalancePreCheck(query);
    }

    @Test
    void rejectsWhenAvailableBalanceIsInsufficient() {
        when(query.getForTransfer(101L, 1L)).thenReturn(new AvailableBalanceSnapshot(true, 70_000L));

        var context = new TransferPreCheckContext(1L, 101L, 80_000L);

        assertThatThrownBy(() -> preCheck.check(context))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(LmtErrorCode.INSUFFICIENT_WITHDRAWABLE_AMOUNT));
    }

    @Test
    void allowsAmountEqualToAvailableBalance() {
        when(query.getForTransfer(101L, 1L)).thenReturn(new AvailableBalanceSnapshot(true, 70_000L));

        var context = new TransferPreCheckContext(1L, 101L, 70_000L);

        assertThatCode(() -> preCheck.check(context)).doesNotThrowAnyException();
    }

    @Test
    void defersInactiveAccountToExistingTransferValidation() {
        when(query.getForTransfer(101L, 1L)).thenReturn(new AvailableBalanceSnapshot(false, 0L));

        var context = new TransferPreCheckContext(1L, 101L, 80_000L);

        assertThatCode(() -> preCheck.check(context)).doesNotThrowAnyException();
    }
}
