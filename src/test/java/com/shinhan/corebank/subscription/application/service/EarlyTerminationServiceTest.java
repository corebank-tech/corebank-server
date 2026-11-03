package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.api.AccountTransferLifecycleLockUseCase;
import com.shinhan.corebank.account.api.EarlyTerminationAccountSettlementUseCase;
import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.autotransfer.api.AutoTransferAccountClosureUseCase;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.scheduledtransfer.api.ScheduledTransferAccountClosureUseCase;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationResult;
import com.shinhan.corebank.subscription.api.InterestPaymentCommand;
import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.api.InterestPaymentResult;
import com.shinhan.corebank.subscription.api.InterestPaymentUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccountNumberQueryPort;
import com.shinhan.corebank.subscription.application.port.out.MaturityTerminationPasswordVerificationPort;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import com.shinhan.corebank.transfer.api.LedgerPrincipalBalanceQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EarlyTerminationServiceTest {
    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 200L;
    private static final LocalDate OPENED = LocalDate.of(2026, 1, 1);
    private static final LocalDate TERMINATED = LocalDate.of(2026, 11, 2);
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 2);

    @Mock
    private BusinessDateGate gate;

    @Mock
    private EarlyTerminationValidationService validationService;

    @Mock
    private MaturityTerminationPasswordVerificationPort passwordPort;

    @Mock
    private InterestPaymentUseCase interestUseCase;

    @Mock
    private MaturityAccountSettlementUseCase balanceUseCase;

    @Mock
    private LedgerPrincipalBalanceQuery principalQuery;

    @Mock
    private LedgerDepositUseCase ledgerUseCase;

    @Mock
    private MaturityAccountQueryUseCase destinationUseCase;

    @Mock
    private JournalPostingUseCase journalPostingUseCase;

    @Mock
    private EarlyTerminationAccountSettlementUseCase settlementUseCase;

    @Mock
    private AccountNumberQueryPort accountNumberQueryPort;

    @Mock
    private AutoTransferAccountClosureUseCase autoTransferClosureUseCase;

    @Mock
    private ScheduledTransferAccountClosureUseCase scheduledTransferClosureUseCase;

    @Mock
    private AccountTransferLifecycleLockUseCase lifecycleLockUseCase;

    @Test
    void terminationPaysInterestThenTransfersPrincipalAndClosesWithSameTradeDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), ZoneId.of("UTC"));
        EarlyTerminationService service = new EarlyTerminationService(
                gate,
                validationService,
                passwordPort,
                interestUseCase,
                balanceUseCase,
                principalQuery,
                ledgerUseCase,
                destinationUseCase,
                journalPostingUseCase,
                settlementUseCase,
                accountNumberQueryPort,
                autoTransferClosureUseCase,
                scheduledTransferClosureUseCase,
                lifecycleLockUseCase,
                clock);
        EarlyTerminationCommand command = new EarlyTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, "token");
        when(validationService.validate(command))
                .thenReturn(new EarlyTerminationValidationService.Validated(
                        ACCOUNT_ID,
                        WITHDRAWAL_ACCOUNT_ID,
                        OPENED,
                        TERMINATED,
                        new BigDecimal("1.80"),
                        1_000_000L,
                        true));

        when(accountNumberQueryPort.findAccountNumber(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(Optional.of("088236999999"));

        when(interestUseCase.pay(any(InterestPaymentCommand.class)))
                .thenReturn(new InterestPaymentResult(10000L, 1540L, 8460L, "interest", TRADE_DATE));
        when(balanceUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(1_008_460L);
        when(ledgerUseCase.transferBetween(
                        ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, 1_008_460L, DepositType.TERMINATION, DepositChannel.WB))
                .thenReturn(new LedgerDepositResult("transfer", 1_008_460L, TRADE_DATE));

        EarlyTerminationResult result = service.terminate(command);
        assertThat(result.principal()).isEqualTo(1_000_000L);
        assertThat(result.totalTransferred()).isEqualTo(1_008_460L);
        assertThat(result.tradeDate()).isEqualTo(TRADE_DATE);

        ArgumentCaptor<InterestPaymentCommand> interestCaptor = ArgumentCaptor.forClass(InterestPaymentCommand.class);
        ArgumentCaptor<JournalRequest> journalCaptor = ArgumentCaptor.forClass(JournalRequest.class);
        InOrder order = inOrder(
                gate,
                validationService,
                passwordPort,
                lifecycleLockUseCase,
                accountNumberQueryPort,
                autoTransferClosureUseCase,
                scheduledTransferClosureUseCase,
                interestUseCase,
                balanceUseCase,
                ledgerUseCase,
                destinationUseCase,
                journalPostingUseCase,
                settlementUseCase);
        order.verify(gate).acquireSharedAndReadBusinessDate();
        order.verify(validationService).validate(command);
        order.verify(passwordPort).verifyAndConsumeAccountPasswordToken("token", CUSTOMER_ID, ACCOUNT_ID);
        order.verify(lifecycleLockUseCase).lockForEarlyTermination(CUSTOMER_ID, ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID);

        order.verify(accountNumberQueryPort).findAccountNumber(ACCOUNT_ID, CUSTOMER_ID);

        order.verify(autoTransferClosureUseCase)
                .terminateLinkedTransfers(eq(ACCOUNT_ID), eq("088236999999"), any(LocalDateTime.class));

        order.verify(scheduledTransferClosureUseCase)
                .cancelLinkedTransfers(eq(ACCOUNT_ID), eq("088236999999"), any(LocalDateTime.class));

        order.verify(interestUseCase).pay(interestCaptor.capture());
        order.verify(balanceUseCase).requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID);
        order.verify(ledgerUseCase)
                .transferBetween(
                        ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, 1_008_460L, DepositType.TERMINATION, DepositChannel.WB);
        order.verify(destinationUseCase).requireWithdrawalDestinationForUpdate(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID);
        order.verify(journalPostingUseCase).post(journalCaptor.capture());
        order.verify(settlementUseCase)
                .closeEarly(ACCOUNT_ID, CUSTOMER_ID, LocalDateTime.of(2026, 11, 2, 12, 0), TRADE_DATE);

        assertThat(interestCaptor.getValue().reason()).isEqualTo(InterestPaymentReason.TERMINATION);
        assertThat(interestCaptor.getValue().calculationMode())
                .isEqualTo(InterestPaymentCommand.CalculationMode.SIMPLE);
        assertThat(interestCaptor.getValue().annualRate()).isEqualByComparingTo("1.80");
        assertThat(journalCaptor.getValue().txType()).isEqualTo(GlTxType.TERMINATION);
    }
}
