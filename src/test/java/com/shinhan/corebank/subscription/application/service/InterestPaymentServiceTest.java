package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.subscription.api.InterestPaymentCommand;
import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.api.InterestPaymentResult;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationUseCase;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort.InterestPaymentRecord;
import com.shinhan.corebank.subscription.domain.InterestResult;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterestPaymentServiceTest {

    private static final long ACCOUNT_ID = 68701L;
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 21);
    private static final String TRANSACTION_NUMBER = "20261021BT0000000001";
    private static final BigDecimal RATE = new BigDecimal("3.70");

    @Mock
    private BusinessDateGate businessDateGate;

    @Mock
    private InterestCalculationUseCase calculationUseCase;

    @Mock
    private LedgerDepositUseCase ledgerDepositUseCase;

    @Mock
    private JournalPostingUseCase journalPostingUseCase;

    @Mock
    private InterestPaymentHistoryPort historyPort;

    private InterestPaymentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T06:00:00Z"), ZoneId.of("Asia/Seoul"));

        service = new InterestPaymentService(
                businessDateGate, calculationUseCase, ledgerDepositUseCase, journalPostingUseCase, historyPort, clock);
    }

    @Test
    @DisplayName("정상 이자 지급: 세후이자를 원장에 입금하고 GL 및 이력을 기록한다")
    void pay_success() {
        // given
        InterestPaymentCommand command = maturityCommand();

        when(calculationUseCase.calculate(any())).thenReturn(new InterestResult(10_000L, 1_540L, 8_460L, RATE));

        when(ledgerDepositUseCase.credit(ACCOUNT_ID, 8_460L, DepositType.INTEREST, DepositChannel.BT))
                .thenReturn(new LedgerDepositResult(TRANSACTION_NUMBER, 1_008_460L, TRADE_DATE));

        // when
        InterestPaymentResult result = service.pay(command);

        // then
        assertThat(result.gross()).isEqualTo(10_000L);
        assertThat(result.tax()).isEqualTo(1_540L);
        assertThat(result.net()).isEqualTo(8_460L);
        assertThat(result.transactionNumber()).isEqualTo(TRANSACTION_NUMBER);
        assertThat(result.tradeDate()).isEqualTo(TRADE_DATE);

        ArgumentCaptor<JournalRequest> journalCaptor = ArgumentCaptor.forClass(JournalRequest.class);
        verify(journalPostingUseCase).post(journalCaptor.capture());

        JournalRequest journal = journalCaptor.getValue();

        assertThat(journal.txType()).isEqualTo(GlTxType.INTEREST);
        assertThat(journal.referenceKey()).isEqualTo(TRANSACTION_NUMBER);
        assertThat(journal.tradeDate()).isEqualTo(TRADE_DATE);

        assertThat(journal.lines())
                .containsExactly(
                        new JournalLine("50100", JournalDirection.DEBIT, 10_000L),
                        new JournalLine("20100", JournalDirection.CREDIT, 10_000L),
                        new JournalLine("20100", JournalDirection.DEBIT, 1_540L),
                        new JournalLine("20200", JournalDirection.CREDIT, 1_540L));

        ArgumentCaptor<InterestPaymentRecord> historyCaptor = ArgumentCaptor.forClass(InterestPaymentRecord.class);
        verify(historyPort).record(historyCaptor.capture());

        InterestPaymentRecord history = historyCaptor.getValue();

        assertThat(history.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(history.reason()).isEqualTo(InterestPaymentReason.MATURITY);
        assertThat(history.gross()).isEqualTo(10_000L);
        assertThat(history.tax()).isEqualTo(1_540L);
        assertThat(history.net()).isEqualTo(8_460L);
        assertThat(history.transactionNumber()).isEqualTo(TRANSACTION_NUMBER);
        assertThat(history.tradeDate()).isEqualTo(TRADE_DATE);

        verify(businessDateGate).acquireSharedAndReadBusinessDate();
    }

    @Test
    @DisplayName("이자가 0원이면 원장과 GL 없이 지급 이력만 기록한다")
    void pay_zeroInterest() {
        // given
        when(calculationUseCase.calculate(any())).thenReturn(new InterestResult(0L, 0L, 0L, RATE));

        // when
        InterestPaymentResult result = service.pay(maturityCommand());

        // then
        assertThat(result.gross()).isZero();
        assertThat(result.tax()).isZero();
        assertThat(result.net()).isZero();
        assertThat(result.transactionNumber()).isNull();
        assertThat(result.tradeDate()).isNull();

        verifyNoInteractions(ledgerDepositUseCase, journalPostingUseCase);

        ArgumentCaptor<InterestPaymentRecord> captor = ArgumentCaptor.forClass(InterestPaymentRecord.class);
        verify(historyPort).record(captor.capture());

        assertThat(captor.getValue().net()).isZero();
        assertThat(captor.getValue().transactionNumber()).isNull();
        assertThat(captor.getValue().tradeDate()).isNull();
    }

    @Test
    @DisplayName("세금이 0원이면 이자비용과 예수금 분개만 생성한다")
    void pay_zeroTax() {
        // given
        when(calculationUseCase.calculate(any())).thenReturn(new InterestResult(50L, 0L, 50L, RATE));

        when(ledgerDepositUseCase.credit(ACCOUNT_ID, 50L, DepositType.INTEREST, DepositChannel.BT))
                .thenReturn(new LedgerDepositResult(TRANSACTION_NUMBER, 1_000_050L, TRADE_DATE));

        // when
        service.pay(maturityCommand());

        // then
        ArgumentCaptor<JournalRequest> captor = ArgumentCaptor.forClass(JournalRequest.class);
        verify(journalPostingUseCase).post(captor.capture());

        assertThat(captor.getValue().lines())
                .containsExactly(
                        new JournalLine("50100", JournalDirection.DEBIT, 50L),
                        new JournalLine("20100", JournalDirection.CREDIT, 50L));
    }

    @Test
    @DisplayName("이미 지급한 계좌면 중복 지급을 거부한다")
    void pay_alreadyPaid() {
        // given
        when(historyPort.existsByAccountId(ACCOUNT_ID)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.pay(maturityCommand()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(SubscriptionErrorCode.INTEREST_ALREADY_PAID));

        verifyNoInteractions(calculationUseCase, ledgerDepositUseCase, journalPostingUseCase);
        verify(historyPort, never()).record(any());
    }

    @Test
    @DisplayName("GL 기표가 실패하면 예외를 전파하고 지급 이력을 저장하지 않는다")
    void pay_glFailure() {
        // given
        when(calculationUseCase.calculate(any())).thenReturn(new InterestResult(10_000L, 1_540L, 8_460L, RATE));

        when(ledgerDepositUseCase.credit(ACCOUNT_ID, 8_460L, DepositType.INTEREST, DepositChannel.BT))
                .thenReturn(new LedgerDepositResult(TRANSACTION_NUMBER, 1_008_460L, TRADE_DATE));

        doThrow(new IllegalStateException("GL 기표 실패"))
                .when(journalPostingUseCase)
                .post(any(JournalRequest.class));

        // when & then
        assertThatThrownBy(() -> service.pay(maturityCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GL 기표 실패");

        verify(historyPort, never()).record(any());
    }

    private InterestPaymentCommand maturityCommand() {
        return new InterestPaymentCommand(
                ACCOUNT_ID,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 21),
                RATE,
                InterestPaymentCommand.CalculationMode.SIMPLE,
                DepositChannel.BT,
                InterestPaymentReason.MATURITY);
    }
}
