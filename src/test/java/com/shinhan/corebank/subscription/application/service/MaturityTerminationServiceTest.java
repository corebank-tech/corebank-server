package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.product.api.InterestCalculationMode;
import com.shinhan.corebank.subscription.api.InterestPaymentCommand;
import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.api.InterestPaymentResult;
import com.shinhan.corebank.subscription.api.InterestPaymentUseCase;
import com.shinhan.corebank.subscription.api.MaturityTerminationCommand;
import com.shinhan.corebank.subscription.api.MaturityTerminationResult;
import com.shinhan.corebank.subscription.application.port.out.MaturityTerminationPasswordVerificationPort;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaturityTerminationServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 200L;

    private static final LocalDate OPENED_DATE = LocalDate.of(2025, 10, 22);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 10, 22);
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 22);

    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 10, 22, 12, 0);

    private static final BigDecimal RATE = new BigDecimal("3.70");

    private static final long PRINCIPAL = 1_000_000L;
    private static final long GROSS_INTEREST = 30_000L;
    private static final long TAX = 4_620L;
    private static final long NET_INTEREST = 25_380L;
    private static final long TOTAL_TRANSFERRED = 1_025_380L;

    private static final String TRANSACTION_NUMBER = "20261022WB0000000001";
    private static final String AUTH_TOKEN = "test-account-password-token";

    private static final MaturityTerminationCommand COMMAND =
            new MaturityTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, AUTH_TOKEN);

    @Mock
    private BusinessDateGate businessDateGate;

    @Mock
    private MaturityTerminationValidationService validationService;

    @Mock
    private InterestPaymentUseCase interestPaymentUseCase;

    @Mock
    private MaturityAccountSettlementUseCase settlementUseCase;

    @Mock
    private LedgerDepositUseCase ledgerDepositUseCase;

    @Mock
    private MaturityAccountQueryUseCase maturityAccountQueryUseCase;

    @Mock
    private MaturityTerminationPasswordVerificationPort passwordVerificationPort;

    @Mock
    private JournalPostingUseCase journalPostingUseCase;

    private MaturityTerminationService service;

    @BeforeEach
    void setUp() {
        // UTC 03:00 = KST 12:00
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("UTC"));

        service = new MaturityTerminationService(
                businessDateGate,
                validationService,
                interestPaymentUseCase,
                settlementUseCase,
                ledgerDepositUseCase,
                maturityAccountQueryUseCase,
                passwordVerificationPort,
                journalPostingUseCase,
                clock);
    }

    @Test
    @DisplayName("정상 만기해지는 원리금을 이체하고 계좌를 종료한다")
    void terminate_success() {
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED);
        stubTransfer(TOTAL_TRANSFERRED, validTransfer());

        MaturityTerminationResult result = service.terminate(COMMAND);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.withdrawalAccountId()).isEqualTo(WITHDRAWAL_ACCOUNT_ID);
        assertThat(result.principal()).isEqualTo(PRINCIPAL);
        assertThat(result.grossInterest()).isEqualTo(GROSS_INTEREST);
        assertThat(result.tax()).isEqualTo(TAX);
        assertThat(result.netInterest()).isEqualTo(NET_INTEREST);
        assertThat(result.totalTransferred()).isEqualTo(TOTAL_TRANSFERRED);
        assertThat(result.transactionNumber()).isEqualTo(TRANSACTION_NUMBER);
        assertThat(result.tradeDate()).isEqualTo(TRADE_DATE);
        assertThat(result.closedAt()).isEqualTo(CLOSED_AT);

        ArgumentCaptor<InterestPaymentCommand> captor = ArgumentCaptor.forClass(InterestPaymentCommand.class);

        InOrder order = inOrder(
                businessDateGate,
                validationService,
                passwordVerificationPort,
                interestPaymentUseCase,
                settlementUseCase,
                ledgerDepositUseCase,
                journalPostingUseCase,
                maturityAccountQueryUseCase);

        order.verify(businessDateGate).acquireSharedAndReadBusinessDate();

        order.verify(validationService).validate(COMMAND);

        order.verify(passwordVerificationPort)
                .verifyAndConsumeAccountPasswordToken("test-account-password-token", CUSTOMER_ID, ACCOUNT_ID);

        order.verify(interestPaymentUseCase).pay(captor.capture());

        order.verify(settlementUseCase).requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID);

        order.verify(ledgerDepositUseCase)
                .transferBetween(
                        ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, TOTAL_TRANSFERRED, DepositType.MATURITY, DepositChannel.WB);

        ArgumentCaptor<JournalRequest> journalCaptor = ArgumentCaptor.forClass(JournalRequest.class);

        order.verify(maturityAccountQueryUseCase)
                .requireWithdrawalDestinationForUpdate(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID);

        order.verify(journalPostingUseCase).post(journalCaptor.capture());

        JournalRequest journal = journalCaptor.getValue();

        assertThat(journal.txType()).isEqualTo(GlTxType.MATURITY);
        assertThat(journal.referenceKey()).isEqualTo(TRANSACTION_NUMBER);
        assertThat(journal.tradeDate()).isEqualTo(TRADE_DATE);

        assertThat(journal.lines())
                .containsExactly(
                        new JournalLine("20100", JournalDirection.DEBIT, TOTAL_TRANSFERRED),
                        new JournalLine("20100", JournalDirection.CREDIT, TOTAL_TRANSFERRED));

        order.verify(settlementUseCase).closeMatured(ACCOUNT_ID, CUSTOMER_ID, CLOSED_AT, TRADE_DATE);

        InterestPaymentCommand interestCommand = captor.getValue();

        assertThat(interestCommand.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(interestCommand.openedDate()).isEqualTo(OPENED_DATE);
        assertThat(interestCommand.toExclusive()).isEqualTo(MATURITY_DATE);
        assertThat(interestCommand.annualRate()).isEqualByComparingTo(RATE);
        assertThat(interestCommand.calculationMode()).isEqualTo(InterestPaymentCommand.CalculationMode.SIMPLE);
        assertThat(interestCommand.channel()).isEqualTo(DepositChannel.WB);
        assertThat(interestCommand.reason()).isEqualTo(InterestPaymentReason.MATURITY);
    }

    @Test
    @DisplayName("계좌비밀번호 인증 토큰이 무효하면 이자 지급과 원리금 이체를 실행하지 않는다")
    void terminate_invalidPasswordToken() {
        // given
        // 사전검증은 통과시키고 인증 단계에서만 실패하도록 설정한다.
        // 인증 직후 예외가 발생하므로 Validated의 실제 필드값은 사용되지 않는다.
        MaturityTerminationValidationService.Validated validated =
                mock(MaturityTerminationValidationService.Validated.class);

        when(validationService.validate(COMMAND)).thenReturn(validated);

        doThrow(new BusinessException(AccountPasswordErrorCode.INVALID_AUTH_TOKEN))
                .when(passwordVerificationPort)
                .verifyAndConsumeAccountPasswordToken(
                        COMMAND.accountPasswordAuthToken(), COMMAND.customerId(), COMMAND.accountId());

        // when & then
        assertThatThrownBy(() -> service.terminate(COMMAND))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("APW0102"));

        // 사전검증까지는 정상적으로 실행된다.
        verify(validationService).validate(COMMAND);

        // 계좌비밀번호 인증 토큰 검증이 실행된다.
        verify(passwordVerificationPort)
                .verifyAndConsumeAccountPasswordToken(
                        COMMAND.accountPasswordAuthToken(), COMMAND.customerId(), COMMAND.accountId());

        // 인증 실패 후 금융 처리는 전혀 실행되지 않아야 한다.
        verifyNoInteractions(
                interestPaymentUseCase, ledgerDepositUseCase, maturityAccountQueryUseCase, settlementUseCase);
    }

    @Test
    @DisplayName("월복리 상품은 PH-14에 월복리 계산 방식을 전달한다")
    void terminate_monthlyCompound() {
        stubBeforeTransfer(validated(InterestCalculationMode.MONTHLY_COMPOUND), normalInterest(), TOTAL_TRANSFERRED);
        stubTransfer(TOTAL_TRANSFERRED, validTransfer());

        service.terminate(COMMAND);

        ArgumentCaptor<InterestPaymentCommand> captor = ArgumentCaptor.forClass(InterestPaymentCommand.class);

        verify(interestPaymentUseCase).pay(captor.capture());

        assertThat(captor.getValue().calculationMode())
                .isEqualTo(InterestPaymentCommand.CalculationMode.MONTHLY_COMPOUND);
    }

    @Test
    @DisplayName("이자가 0원이어도 원금이 있으면 만기해지한다")
    void terminate_zeroInterest() {
        stubBeforeTransfer(
                validated(InterestCalculationMode.SIMPLE),
                new InterestPaymentResult(0L, 0L, 0L, null, null),
                PRINCIPAL);

        stubTransfer(PRINCIPAL, new LedgerDepositResult(TRANSACTION_NUMBER, PRINCIPAL, TRADE_DATE));

        MaturityTerminationResult result = service.terminate(COMMAND);

        assertThat(result.principal()).isEqualTo(PRINCIPAL);
        assertThat(result.netInterest()).isZero();
        assertThat(result.totalTransferred()).isEqualTo(PRINCIPAL);

        verify(ledgerDepositUseCase)
                .transferBetween(ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, PRINCIPAL, DepositType.MATURITY, DepositChannel.WB);

        verify(settlementUseCase).closeMatured(ACCOUNT_ID, CUSTOMER_ID, CLOSED_AT, TRADE_DATE);
    }

    @Test
    @DisplayName("원리금이 0원이면 이체하지 않고 거부한다")
    void terminate_zeroPayout() {
        stubBeforeTransfer(
                validated(InterestCalculationMode.SIMPLE), new InterestPaymentResult(0L, 0L, 0L, null, null), 0L);

        assertError(COMMAND, SubscriptionErrorCode.MATURITY_PAYOUT_AMOUNT_ZERO);

        verifyNoInteractions(ledgerDepositUseCase, maturityAccountQueryUseCase);

        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("잔액이 세후이자보다 작으면 정합성 오류로 거부한다")
    void terminate_balanceLessThanInterest() {
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), NET_INTEREST - 1);

        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);

        verifyNoInteractions(ledgerDepositUseCase);
        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PH-14에서 이자 중복 지급을 거부하면 후속 처리를 중단한다")
    void terminate_alreadyPaid() {
        when(validationService.validate(COMMAND)).thenReturn(validated(InterestCalculationMode.SIMPLE));

        when(interestPaymentUseCase.pay(any(InterestPaymentCommand.class)))
                .thenThrow(new BusinessException(SubscriptionErrorCode.INTEREST_ALREADY_PAID));

        assertError(COMMAND, SubscriptionErrorCode.INTEREST_ALREADY_PAID);

        verifyNoInteractions(settlementUseCase, ledgerDepositUseCase, maturityAccountQueryUseCase);
    }

    @Test
    @DisplayName("MATURITY 원장 이체가 실패하면 계좌를 종료하지 않는다")
    void terminate_transferFailure() {
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED);

        when(ledgerDepositUseCase.transferBetween(
                        ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, TOTAL_TRANSFERRED, DepositType.MATURITY, DepositChannel.WB))
                .thenThrow(new IllegalStateException("원장 저장 실패"));

        assertThatThrownBy(() -> service.terminate(COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("원장 저장 실패");

        verifyNoInteractions(maturityAccountQueryUseCase);
        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("수취 계좌 잠금 후 재검증 실패 시 계좌 종료를 중단한다")
    void terminate_withdrawalRevalidationFailure() {
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED);
        stubTransfer(TOTAL_TRANSFERRED, validTransfer());

        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN))
                .when(maturityAccountQueryUseCase)
                .requireWithdrawalDestinationForUpdate(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID);

        assertError(COMMAND, CommonErrorCode.FORBIDDEN);

        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("계좌 CLOSED 저장 실패 시 예외를 전파한다")
    void terminate_closeFailure() {
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED);
        stubTransfer(TOTAL_TRANSFERRED, validTransfer());

        doThrow(new IllegalStateException("계좌 종료 저장 실패"))
                .when(settlementUseCase)
                .closeMatured(ACCOUNT_ID, CUSTOMER_ID, CLOSED_AT, TRADE_DATE);

        assertThatThrownBy(() -> service.terminate(COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 종료 저장 실패");
    }

    @Test
    @DisplayName("MATURITY GL 기표 실패 시 계좌 종료를 실행하지 않고 예외를 전파한다")
    void terminate_maturityGlFailure() {
        // given
        // 사전검증, 이자 지급, 원리금 잔액 조회까지 정상 처리
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED);

        // MATURITY 원리금 이체 성공
        stubTransfer(TOTAL_TRANSFERRED, validTransfer());

        // GL 전표 기표 시 실패 발생
        doThrow(new IllegalStateException("MATURITY GL 실패"))
                .when(journalPostingUseCase)
                .post(any(JournalRequest.class));

        // when & then
        assertThatThrownBy(() -> service.terminate(COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MATURITY GL 실패");

        // 원리금 전표 기표까지 도달했는지 확인
        verify(journalPostingUseCase).post(any(JournalRequest.class));

        // 수취 계좌 재검증은 완료됐고, GL 실패 이후 CLOSED 전이는 실행되지 않아야 한다.
        verify(maturityAccountQueryUseCase).requireWithdrawalDestinationForUpdate(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID);
        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이체 결과에 거래번호가 없으면 계좌를 종료하지 않는다")
    void terminate_missingTransactionNumber() {
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED);

        stubTransfer(TOTAL_TRANSFERRED, new LedgerDepositResult(null, TOTAL_TRANSFERRED, TRADE_DATE));

        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);

        verifyNoInteractions(maturityAccountQueryUseCase);
        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("정기예금 실제 원금이 계약 원금과 다르면 이체와 계좌 종료를 거부한다")
    void terminate_principalMismatch() {
        // 계약 원금은 100만 원이지만 실제 원금이 1원 더 많다.
        stubBeforeTransfer(validated(InterestCalculationMode.SIMPLE), normalInterest(), TOTAL_TRANSFERRED + 1);

        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);

        verifyNoInteractions(ledgerDepositUseCase, journalPostingUseCase);
        verify(settlementUseCase, never()).closeMatured(any(), any(), any(), any());
    }

    @Test
    @DisplayName("정기적금은 가입 납입액과 누적 원금이 달라도 단순 비교로 거부하지 않는다")
    void terminate_installmentDoesNotCompareInitialAmount() {
        long additionalPayments = 500_000L;
        long totalTransferred = TOTAL_TRANSFERRED + additionalPayments;

        var validated = new MaturityTerminationValidationService.Validated(
                ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID,
                OPENED_DATE,
                MATURITY_DATE,
                RATE,
                InterestCalculationMode.SIMPLE,
                PRINCIPAL,
                false);

        stubBeforeTransfer(validated, normalInterest(), totalTransferred);

        stubTransfer(totalTransferred, new LedgerDepositResult(TRANSACTION_NUMBER, totalTransferred, TRADE_DATE));

        MaturityTerminationResult result = service.terminate(COMMAND);

        assertThat(result.principal()).isEqualTo(PRINCIPAL + additionalPayments);
        assertThat(result.totalTransferred()).isEqualTo(totalTransferred);
    }

    private void assertError(MaturityTerminationCommand command, ErrorCode expectedErrorCode) {

        assertThatThrownBy(() -> service.terminate(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expectedErrorCode);
    }

    private static MaturityTerminationValidationService.Validated validated(InterestCalculationMode mode) {
        return new MaturityTerminationValidationService.Validated(
                ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, OPENED_DATE, MATURITY_DATE, RATE, mode, PRINCIPAL, true);
    }

    private static InterestPaymentResult normalInterest() {
        return new InterestPaymentResult(GROSS_INTEREST, TAX, NET_INTEREST, "20261022WB0000000002", TRADE_DATE);
    }

    private static LedgerDepositResult validTransfer() {
        return new LedgerDepositResult(TRANSACTION_NUMBER, TOTAL_TRANSFERRED, TRADE_DATE);
    }

    private void stubBeforeTransfer(
            MaturityTerminationValidationService.Validated validated, InterestPaymentResult interest, long balance) {

        when(validationService.validate(COMMAND)).thenReturn(validated);

        when(interestPaymentUseCase.pay(any(InterestPaymentCommand.class))).thenReturn(interest);

        when(settlementUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(balance);
    }

    private void stubTransfer(long amount, LedgerDepositResult result) {

        when(ledgerDepositUseCase.transferBetween(
                        ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, amount, DepositType.MATURITY, DepositChannel.WB))
                .thenReturn(result);
    }
}
