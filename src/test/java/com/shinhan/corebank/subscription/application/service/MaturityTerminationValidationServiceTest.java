package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.api.MaturityAccountQueryUseCase;
import com.shinhan.corebank.account.api.MaturityAccountView;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.product.api.InterestCalculationMode;
import com.shinhan.corebank.product.api.ProductInterestPolicyQueryUseCase;
import com.shinhan.corebank.subscription.api.MaturityTerminationCommand;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaturityTerminationValidationServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final Long WITHDRAWAL_ACCOUNT_ID = 200L;
    private static final Long PRODUCT_ID = 10L;

    private static final LocalDate OPENED_DATE = LocalDate.of(2025, 10, 22);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 10, 22);

    private static final BigDecimal RATE = new BigDecimal("3.70");

    private static final MaturityTerminationCommand COMMAND =
            new MaturityTerminationCommand(CUSTOMER_ID, ACCOUNT_ID, "test-account-password-token");

    @Mock
    private ProductSubscriptionQueryPort subscriptionQueryPort;

    @Mock
    private MaturityAccountQueryUseCase maturityAccountQueryUseCase;

    @Mock
    private ProductInterestPolicyQueryUseCase productInterestPolicyQueryUseCase;

    private MaturityTerminationValidationService service;

    @BeforeEach
    void setUp() {
        // UTC 10/21 15:30 = KST 10/22 00:30
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);

        service = new MaturityTerminationValidationService(
                subscriptionQueryPort, maturityAccountQueryUseCase, productInterestPolicyQueryUseCase, clock);
    }

    @Test
    @DisplayName("정상 만기 계좌는 이자 계산에 필요한 정보를 반환한다")
    void validate_success() {
        stubValidSubscription();
        stubValidProductAccount();
        stubCalculationMode(InterestCalculationMode.SIMPLE);

        var result = service.validate(COMMAND);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.withdrawalAccountId()).isEqualTo(WITHDRAWAL_ACCOUNT_ID);
        assertThat(result.openedDate()).isEqualTo(OPENED_DATE);
        assertThat(result.maturityDate()).isEqualTo(MATURITY_DATE);
        assertThat(result.annualRate()).isEqualByComparingTo(RATE);
        assertThat(result.calculationMode()).isEqualTo(InterestCalculationMode.SIMPLE);

        verify(maturityAccountQueryUseCase).requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE);

        verify(maturityAccountQueryUseCase).requireWithdrawalDestination(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID);
    }

    @Test
    @DisplayName("월복리 상품은 MONTHLY_COMPOUND를 반환한다")
    void validate_compound() {
        stubValidSubscription();
        stubValidProductAccount();
        stubCalculationMode(InterestCalculationMode.MONTHLY_COMPOUND);

        var result = service.validate(COMMAND);

        assertThat(result.calculationMode()).isEqualTo(InterestCalculationMode.MONTHLY_COMPOUND);
    }

    @Test
    @DisplayName("요청이 null이면 필수 입력 오류를 반환한다")
    void validate_nullCommand() {
        assertError(null, CommonErrorCode.REQUIRED_FIELD_MISSING);

        verifyNoInteractions(subscriptionQueryPort, maturityAccountQueryUseCase, productInterestPolicyQueryUseCase);
    }

    @Test
    @DisplayName("계좌 ID가 0이면 입력 오류를 반환한다")
    void validate_invalidAccountId() {
        assertError(
                new MaturityTerminationCommand(CUSTOMER_ID, 0L, "test-account-password-token"),
                CommonErrorCode.INVALID_INPUT);

        verifyNoInteractions(subscriptionQueryPort, maturityAccountQueryUseCase, productInterestPolicyQueryUseCase);
    }

    @Test
    @DisplayName("고객 ID가 null이면 필수 입력 오류를 반환한다")
    void validate_nullCustomerId() {
        assertError(
                new MaturityTerminationCommand(null, ACCOUNT_ID, "test-account-password-token"),
                CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("가입 정보가 없으면 만기해지를 거부한다")
    void validate_subscriptionNotFound() {
        when(subscriptionQueryPort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertError(COMMAND, SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND);

        verifyNoInteractions(maturityAccountQueryUseCase, productInterestPolicyQueryUseCase);
    }

    @Test
    @DisplayName("가입이 완료되지 않은 계좌는 거부한다")
    void validate_subscriptionNotCompleted() {
        stubSubscription(subscription(
                ProcessResultStatus.PROCESSING, WITHDRAWAL_ACCOUNT_ID, PRODUCT_ID, OPENED_DATE, MATURITY_DATE, RATE));

        assertError(COMMAND, SubscriptionErrorCode.SUBSCRIPTION_NOT_COMPLETED);

        verifyNoInteractions(maturityAccountQueryUseCase, productInterestPolicyQueryUseCase);
    }

    @Test
    @DisplayName("계좌 공개 계약에서 거부하면 해당 예외를 전파한다")
    void validate_accountRejected() {
        stubValidSubscription();

        when(maturityAccountQueryUseCase.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE))
                .thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        assertError(COMMAND, CommonErrorCode.FORBIDDEN);

        verifyNoInteractions(productInterestPolicyQueryUseCase);
    }

    @Test
    @DisplayName("가입 상품과 실제 계좌 상품이 다르면 거부한다")
    void validate_productMismatch() {
        stubValidSubscription();

        when(maturityAccountQueryUseCase.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE))
                .thenReturn(new MaturityAccountView(ACCOUNT_ID, 999L, MATURITY_DATE));

        assertError(COMMAND, SubscriptionErrorCode.SUBSCRIPTION_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("가입 만기일과 계좌 만기일이 다르면 거부한다")
    void validate_maturityDateMismatch() {
        stubValidSubscription();

        when(maturityAccountQueryUseCase.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE))
                .thenReturn(new MaturityAccountView(ACCOUNT_ID, PRODUCT_ID, MATURITY_DATE.minusDays(1)));

        assertError(COMMAND, SubscriptionErrorCode.SUBSCRIPTION_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 계좌와 수취 계좌가 같으면 거부한다")
    void validate_sameAccount() {
        stubSubscription(
                subscription(ProcessResultStatus.SUCCESS, ACCOUNT_ID, PRODUCT_ID, OPENED_DATE, MATURITY_DATE, RATE));

        stubValidProductAccount();

        assertError(COMMAND, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("수취 계좌 ID가 없으면 거부한다")
    void validate_missingWithdrawalAccount() {
        stubSubscription(subscription(ProcessResultStatus.SUCCESS, null, PRODUCT_ID, OPENED_DATE, MATURITY_DATE, RATE));

        stubValidProductAccount();

        assertError(COMMAND, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("수취 계좌 공개 계약에서 거부하면 예외를 전파한다")
    void validate_withdrawalAccountRejected() {
        stubValidSubscription();
        stubValidProductAccount();

        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN))
                .when(maturityAccountQueryUseCase)
                .requireWithdrawalDestination(WITHDRAWAL_ACCOUNT_ID, CUSTOMER_ID);

        assertError(COMMAND, CommonErrorCode.FORBIDDEN);

        verifyNoInteractions(productInterestPolicyQueryUseCase);
    }

    @Test
    @DisplayName("적용금리가 없으면 거부한다")
    void validate_missingRate() {
        stubSubscription(subscription(
                ProcessResultStatus.SUCCESS, WITHDRAWAL_ACCOUNT_ID, PRODUCT_ID, OPENED_DATE, MATURITY_DATE, null));

        stubValidProductAccount();

        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("적용금리가 음수이면 거부한다")
    void validate_negativeRate() {
        stubSubscription(subscription(
                ProcessResultStatus.SUCCESS,
                WITHDRAWAL_ACCOUNT_ID,
                PRODUCT_ID,
                OPENED_DATE,
                MATURITY_DATE,
                new BigDecimal("-1.00")));

        stubValidProductAccount();

        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("가입일이 만기일보다 늦으면 거부한다")
    void validate_invalidOpenedDate() {
        stubSubscription(subscription(
                ProcessResultStatus.SUCCESS,
                WITHDRAWAL_ACCOUNT_ID,
                PRODUCT_ID,
                MATURITY_DATE.plusDays(1),
                MATURITY_DATE,
                RATE));

        stubValidProductAccount();

        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("상품 이자 계산 방식이 없으면 거부한다")
    void validate_missingCalculationMode() {
        stubValidSubscription();
        stubValidProductAccount();

        // Mockito의 기본 반환값 null로 계산 방식 누락을 재현한다.
        assertError(COMMAND, CommonErrorCode.INTERNAL_ERROR);

        verify(productInterestPolicyQueryUseCase).getMode(PRODUCT_ID);
    }

    private void assertError(MaturityTerminationCommand command, ErrorCode expectedErrorCode) {

        assertThatThrownBy(() -> service.validate(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expectedErrorCode);
    }

    private void stubValidSubscription() {
        stubSubscription(subscription(
                ProcessResultStatus.SUCCESS, WITHDRAWAL_ACCOUNT_ID, PRODUCT_ID, OPENED_DATE, MATURITY_DATE, RATE));
    }

    private void stubSubscription(ProductSubscription subscription) {
        when(subscriptionQueryPort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(subscription));
    }

    private static ProductSubscription subscription(
            ProcessResultStatus status,
            Long withdrawalAccountId,
            Long productId,
            LocalDate openedDate,
            LocalDate maturityDate,
            BigDecimal rate) {

        return ProductSubscription.builder()
                .subscriptionId(1L)
                .customerId(CUSTOMER_ID)
                .accountId(ACCOUNT_ID)
                .productId(productId)
                .withdrawalAccountId(withdrawalAccountId)
                .status(status)
                .openedDate(openedDate)
                .maturityDate(maturityDate)
                .appliedRate(rate)
                .build();
    }

    private void stubValidProductAccount() {
        when(maturityAccountQueryUseCase.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE))
                .thenReturn(new MaturityAccountView(ACCOUNT_ID, PRODUCT_ID, MATURITY_DATE));
    }

    private void stubCalculationMode(InterestCalculationMode mode) {
        when(productInterestPolicyQueryUseCase.getMode(PRODUCT_ID)).thenReturn(mode);
    }
}
