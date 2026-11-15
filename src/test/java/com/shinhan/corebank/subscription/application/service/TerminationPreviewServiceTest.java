package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.api.MaturityAccountSettlementUseCase;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.api.InterestCalculationMode;
import com.shinhan.corebank.subscription.api.TerminationPreviewResult;
import com.shinhan.corebank.subscription.api.TerminationType;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationUseCase;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort;
import com.shinhan.corebank.subscription.domain.InterestResult;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.LedgerPrincipalBalanceQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminationPreviewServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 200L;

    private static final LocalDate OPENED_DATE = LocalDate.of(2025, 11, 12);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 11, 12);

    private static final BigDecimal RATE = new BigDecimal("3.70");

    @Mock
    private MaturityTerminationValidationService maturityValidationService;

    @Mock
    private EarlyTerminationValidationService earlyValidationService;

    @Mock
    private InterestCalculationUseCase interestCalculationUseCase;

    @Mock
    private InterestPaymentHistoryPort historyPort;

    @Mock
    private MaturityAccountSettlementUseCase balanceUseCase;

    @Mock
    private LedgerPrincipalBalanceQuery ledgerPrincipalBalanceQuery;

    private TerminationPreviewService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T05:30:00Z"), ZoneOffset.UTC);

        service = new TerminationPreviewService(
                maturityValidationService,
                earlyValidationService,
                interestCalculationUseCase,
                historyPort,
                balanceUseCase,
                ledgerPrincipalBalanceQuery,
                clock);
    }

    @Test
    @DisplayName("만기해지 예상 조회는 원금과 세후이자를 합산한다")
    void previewMaturity_success() {
        when(maturityValidationService.validate(CUSTOMER_ID, ACCOUNT_ID))
                .thenReturn(maturityValidated(InterestCalculationMode.SIMPLE, true));

        when(interestCalculationUseCase.calculate(any())).thenReturn(new InterestResult(10_000L, 1_540L, 8_460L, RATE));

        when(balanceUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(1_000_000L);

        TerminationPreviewResult result = service.previewMaturity(CUSTOMER_ID, ACCOUNT_ID);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.withdrawalAccountId()).isEqualTo(WITHDRAWAL_ACCOUNT_ID);
        assertThat(result.terminationType()).isEqualTo(TerminationType.MATURITY);

        assertThat(result.principal()).isEqualTo(1_000_000L);
        assertThat(result.grossInterest()).isEqualTo(10_000L);
        assertThat(result.tax()).isEqualTo(1_540L);
        assertThat(result.netInterest()).isEqualTo(8_460L);
        assertThat(result.totalExpectedAmount()).isEqualTo(1_008_460L);

        assertThat(result.calculationDate()).isEqualTo(LocalDate.of(2026, 11, 12));

        assertThat(result.calculatedAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);

        ArgumentCaptor<InterestCalculationCommand> captor = ArgumentCaptor.forClass(InterestCalculationCommand.class);

        verify(interestCalculationUseCase).calculate(captor.capture());

        assertThat(captor.getValue().mode()).isEqualTo(InterestCalculationCommand.CalculationMode.SIMPLE);
        assertThat(captor.getValue().openedDate()).isEqualTo(OPENED_DATE);
        assertThat(captor.getValue().toExclusive()).isEqualTo(MATURITY_DATE);

        verifyNoInteractions(earlyValidationService, ledgerPrincipalBalanceQuery);
    }

    @Test
    @DisplayName("월복리 적금은 월복리 계산과 원장 원금 검증을 사용한다")
    void previewMaturity_compoundSavings() {
        when(maturityValidationService.validate(CUSTOMER_ID, ACCOUNT_ID))
                .thenReturn(maturityValidated(InterestCalculationMode.MONTHLY_COMPOUND, false));

        when(interestCalculationUseCase.calculate(any()))
                .thenReturn(new InterestResult(20_000L, 3_080L, 16_920L, RATE));

        when(balanceUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(1_000_000L);

        when(ledgerPrincipalBalanceQuery.sumNetAmountExcludingInterest(ACCOUNT_ID))
                .thenReturn(1_000_000L);

        TerminationPreviewResult result = service.previewMaturity(CUSTOMER_ID, ACCOUNT_ID);

        assertThat(result.totalExpectedAmount()).isEqualTo(1_016_920L);

        ArgumentCaptor<InterestCalculationCommand> captor = ArgumentCaptor.forClass(InterestCalculationCommand.class);

        verify(interestCalculationUseCase).calculate(captor.capture());

        assertThat(captor.getValue().mode()).isEqualTo(InterestCalculationCommand.CalculationMode.MONTHLY_COMPOUND);

        verify(ledgerPrincipalBalanceQuery).sumNetAmountExcludingInterest(ACCOUNT_ID);
    }

    @Test
    @DisplayName("중도해지는 경과기간별 금리와 단리 계산을 사용한다")
    void previewEarly_success() {
        LocalDate openedDate = LocalDate.of(2026, 11, 1);
        LocalDate terminationDate = LocalDate.of(2026, 11, 12);
        BigDecimal earlyRate = new BigDecimal("0.25");

        when(earlyValidationService.validate(CUSTOMER_ID, ACCOUNT_ID))
                .thenReturn(new EarlyTerminationValidationService.Validated(
                        ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, openedDate, terminationDate, earlyRate, 100_000L, true));

        when(interestCalculationUseCase.calculate(any())).thenReturn(new InterestResult(0L, 0L, 0L, earlyRate));

        when(balanceUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(100_000L);

        TerminationPreviewResult result = service.previewEarly(CUSTOMER_ID, ACCOUNT_ID);

        assertThat(result.terminationType()).isEqualTo(TerminationType.EARLY_TERMINATION);
        assertThat(result.principal()).isEqualTo(100_000L);
        assertThat(result.netInterest()).isZero();
        assertThat(result.totalExpectedAmount()).isEqualTo(100_000L);

        ArgumentCaptor<InterestCalculationCommand> captor = ArgumentCaptor.forClass(InterestCalculationCommand.class);

        verify(interestCalculationUseCase).calculate(captor.capture());

        assertThat(captor.getValue().mode()).isEqualTo(InterestCalculationCommand.CalculationMode.SIMPLE);
        assertThat(captor.getValue().appliedRate()).isEqualByComparingTo(earlyRate);
        assertThat(captor.getValue().openedDate()).isEqualTo(openedDate);
        assertThat(captor.getValue().toExclusive()).isEqualTo(terminationDate);

        verifyNoInteractions(maturityValidationService, ledgerPrincipalBalanceQuery);
    }

    @Test
    @DisplayName("이미 이자가 지급된 계좌는 예상 조회를 거부한다")
    void previewMaturity_interestAlreadyPaid() {
        when(maturityValidationService.validate(CUSTOMER_ID, ACCOUNT_ID))
                .thenReturn(maturityValidated(InterestCalculationMode.SIMPLE, true));

        when(historyPort.existsByAccountId(ACCOUNT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.previewMaturity(CUSTOMER_ID, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INTEREST_ALREADY_PAID);

        verifyNoInteractions(interestCalculationUseCase, balanceUseCase, ledgerPrincipalBalanceQuery);
    }

    @Test
    @DisplayName("계약 원금과 실제 잔액이 다르면 예상 조회를 거부한다")
    void previewMaturity_principalMismatch() {
        when(maturityValidationService.validate(CUSTOMER_ID, ACCOUNT_ID))
                .thenReturn(maturityValidated(InterestCalculationMode.SIMPLE, true));

        when(interestCalculationUseCase.calculate(any())).thenReturn(new InterestResult(10_000L, 1_540L, 8_460L, RATE));

        when(balanceUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(999_999L);

        assertThatThrownBy(() -> service.previewMaturity(CUSTOMER_ID, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("중도해지 계산 기준일은 조회 시각이 아닌 이자 계산 종료일을 사용한다")
    void previewEarly_usesCalculationEndDate() {
        LocalDate terminationDate = LocalDate.of(2026, 11, 11);
        BigDecimal earlyRate = new BigDecimal("0.25");

        when(earlyValidationService.validate(CUSTOMER_ID, ACCOUNT_ID))
                .thenReturn(new EarlyTerminationValidationService.Validated(
                        ACCOUNT_ID,
                        WITHDRAWAL_ACCOUNT_ID,
                        LocalDate.of(2026, 11, 1),
                        terminationDate,
                        earlyRate,
                        100_000L,
                        true));

        when(interestCalculationUseCase.calculate(any())).thenReturn(new InterestResult(0L, 0L, 0L, earlyRate));

        when(balanceUseCase.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).thenReturn(100_000L);

        TerminationPreviewResult result = service.previewEarly(CUSTOMER_ID, ACCOUNT_ID);

        assertThat(result.calculationDate()).isEqualTo(terminationDate);

        assertThat(result.calculatedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    private static MaturityTerminationValidationService.Validated maturityValidated(
            InterestCalculationMode mode, boolean timeDeposit) {

        return new MaturityTerminationValidationService.Validated(
                ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID, OPENED_DATE, MATURITY_DATE, RATE, mode, 1_000_000L, timeDeposit);
    }
}
