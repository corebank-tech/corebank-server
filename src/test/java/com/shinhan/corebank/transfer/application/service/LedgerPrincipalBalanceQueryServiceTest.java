package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.LedgerPrincipalBalancePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerPrincipalBalanceQueryServiceTest {

    private static final long ACCOUNT_ID = 100L;

    @Mock
    private LedgerPrincipalBalancePort ledgerPrincipalBalancePort;

    @InjectMocks
    private LedgerPrincipalBalanceQueryService service;

    @Test
    void sumNetAmountExcludingInterest_success() {
        when(ledgerPrincipalBalancePort.sumNetAmountExcludingInterest(ACCOUNT_ID))
                .thenReturn(300_000L);

        long result = service.sumNetAmountExcludingInterest(ACCOUNT_ID);

        assertThat(result).isEqualTo(300_000L);
        verify(ledgerPrincipalBalancePort).sumNetAmountExcludingInterest(ACCOUNT_ID);
    }

    @Test
    void sumNetAmountExcludingInterest_zeroAccountId() {
        assertInvalidAccountId(0L);
    }

    @Test
    void sumNetAmountExcludingInterest_negativeAccountId() {
        assertInvalidAccountId(-1L);
    }

    private void assertInvalidAccountId(long accountId) {
        assertThatThrownBy(() -> service.sumNetAmountExcludingInterest(accountId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);

        verifyNoInteractions(ledgerPrincipalBalancePort);
    }
}
