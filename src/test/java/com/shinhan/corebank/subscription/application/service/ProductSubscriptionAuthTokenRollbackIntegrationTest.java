package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaEntity;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordUseCase;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.domain.exception.OtpErrorCode;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaRepository;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteCommand;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteResult;
import com.shinhan.corebank.subscription.application.port.out.SaveTermsAgreementPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// #498: 가입이 롤백되면 그 안에서 소비한 계좌비번·OTP 토큰도 함께 되살아나야 한다.
@DisplayName("상품가입 롤백 시 인증 토큰 소비 롤백 통합 테스트")
class ProductSubscriptionAuthTokenRollbackIntegrationTest extends IntegrationTestSupport {

    private static final String PRODUCT_CODE = "TKN498-SAV-01";
    private static final String ACCOUNT_PASSWORD = "1234";
    private static final long OTP_AMOUNT = 500_000L;
    private static final long MISMATCHED_AMOUNT = 600_000L;
    private static final int TERM_MONTHS = 12;

    @Autowired
    private ProductSubscriptionExecuteUseCase productSubscriptionExecuteUseCase;

    @Autowired
    private VerifyAccountPasswordUseCase verifyAccountPasswordUseCase;

    @Autowired
    private IssueOtpUseCase issueOtpUseCase;

    @Autowired
    private VerifyOtpUseCase verifyOtpUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductRateTierJpaRepository rateTierRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    // 계좌개설 뒤 단계를 실패시켜 두 토큰이 모두 소비된 상태의 롤백을 재현한다. 평소에는 진짜 구현을 부른다.
    @MockitoSpyBean
    private SaveTermsAgreementPort saveTermsAgreementPort;

    private Long customerId;
    private Long productId;
    private Long withdrawalAccountId;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
        productId = seedSavingsProduct();
        withdrawalAccountId = seedWithdrawalAccount();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM product_subscription WHERE product_id = ?", productId);
        new AccountNumberSequenceTestFixture(jdbcTemplate)
                .deleteProductAccountSequence(productId, AccountType.INSTALLMENT_SAVINGS);
        jdbcTemplate.update("DELETE FROM product_rate_tier WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM account WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", withdrawalAccountId);
        jdbcTemplate.update("DELETE FROM product WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM verification_request WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM auth_token WHERE customer_id = ?", customerId);
        customerTestFixture.deleteCustomer(customerId);
    }

    @Test
    @DisplayName("OTP 금액 불일치로 가입이 롤백되면 같은 토큰으로 금액을 바로잡아 다시 가입할 수 있다")
    void execute_afterRollback_sameTokensSucceed() {
        String accountPasswordAuthToken = issueAccountPasswordAuthToken();
        String otpAuthToken = issueOtpAuthToken();

        assertThatThrownBy(() -> productSubscriptionExecuteUseCase.execute(
                        command(MISMATCHED_AMOUNT, accountPasswordAuthToken, otpAuthToken)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(OtpErrorCode.TRANSACTION_MISMATCH));

        ProductSubscriptionExecuteResult result =
                productSubscriptionExecuteUseCase.execute(command(OTP_AMOUNT, accountPasswordAuthToken, otpAuthToken));

        assertThat(result.subscriptionId()).isNotNull();
    }

    @Test
    @DisplayName("가입이 커밋된 뒤에는 같은 토큰으로 다시 가입할 수 없다")
    void execute_afterCommit_sameTokensRejected() {
        String accountPasswordAuthToken = issueAccountPasswordAuthToken();
        String otpAuthToken = issueOtpAuthToken();
        productSubscriptionExecuteUseCase.execute(command(OTP_AMOUNT, accountPasswordAuthToken, otpAuthToken));

        assertThatThrownBy(() -> productSubscriptionExecuteUseCase.execute(
                        command(OTP_AMOUNT, accountPasswordAuthToken, otpAuthToken)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AccountPasswordErrorCode.INVALID_AUTH_TOKEN));
    }

    @Test
    @DisplayName("OTP까지 소비된 뒤 계좌개설 이후 단계에서 롤백돼도 두 토큰을 다시 쓸 수 있다")
    void execute_failsAfterBothTokensConsumed_sameTokensSucceed() {
        String accountPasswordAuthToken = issueAccountPasswordAuthToken();
        String otpAuthToken = issueOtpAuthToken();
        doThrow(new RuntimeException("forced terms agreement save failure"))
                .doCallRealMethod()
                .when(saveTermsAgreementPort)
                .saveAll(anyList());

        assertThatThrownBy(() -> productSubscriptionExecuteUseCase.execute(
                        command(OTP_AMOUNT, accountPasswordAuthToken, otpAuthToken)))
                .hasMessage("forced terms agreement save failure");

        ProductSubscriptionExecuteResult result =
                productSubscriptionExecuteUseCase.execute(command(OTP_AMOUNT, accountPasswordAuthToken, otpAuthToken));

        assertThat(result.subscriptionId()).isNotNull();
    }

    @Test
    @DisplayName("같은 토큰으로 동시에 두 번 가입하면 한 건만 성공한다")
    void execute_concurrentlyWithSameTokens_onlyOneSucceeds() throws Exception {
        String accountPasswordAuthToken = issueAccountPasswordAuthToken();
        String otpAuthToken = issueOtpAuthToken();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> outcomes = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                outcomes.add(executor.submit(() -> {
                    start.await();
                    try {
                        productSubscriptionExecuteUseCase.execute(
                                command(OTP_AMOUNT, accountPasswordAuthToken, otpAuthToken));
                        return null;
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }));
            }
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> outcome : outcomes) {
                Throwable failure = outcome.get(30, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            assertThat(failures)
                    .singleElement()
                    .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(AccountPasswordErrorCode.INVALID_AUTH_TOKEN));
        } finally {
            executor.shutdownNow();
        }
        Long subscriptions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_subscription WHERE product_id = ?", Long.class, productId);
        assertThat(subscriptions).isOne();
    }

    private String issueAccountPasswordAuthToken() {
        return verifyAccountPasswordUseCase
                .verify(new VerifyAccountPasswordCommand(customerId, withdrawalAccountId, ACCOUNT_PASSWORD))
                .accountPasswordAuthToken();
    }

    private String issueOtpAuthToken() {
        IssueOtpResult issued = issueOtpUseCase.issue(new IssueOtpCommand(
                customerId,
                OtpTransactionType.PRODUCT_SUBSCRIPTION,
                Map.of(
                        "productId", productId,
                        "subscriptionAmount", OTP_AMOUNT,
                        "termMonths", TERM_MONTHS,
                        "withdrawalAccountId", withdrawalAccountId)));
        return verifyOtpUseCase
                .verify(new VerifyOtpCommand(customerId, issued.otpRequestId(), issued.otpCode()))
                .otpAuthToken();
    }

    private ProductSubscriptionExecuteCommand command(
            long amount, String accountPasswordAuthToken, String otpAuthToken) {
        return new ProductSubscriptionExecuteCommand(
                customerId,
                productId,
                amount,
                TERM_MONTHS,
                withdrawalAccountId,
                "5678",
                "5678",
                accountPasswordAuthToken,
                otpAuthToken,
                List.of());
    }

    private Long seedSavingsProduct() {
        Long id = productJpaRepository
                .save(ProductJpaEntity.builder()
                        .productCode(PRODUCT_CODE)
                        .productName("토큰 롤백 테스트 적금")
                        .productGroup(ProductGroup.SAVINGS)
                        .depositType(DepositType.INSTALLMENT)
                        .summary("토큰 롤백 테스트용 적금")
                        .description("토큰 롤백 테스트용 적금 설명")
                        .baseRate(new BigDecimal("2.50"))
                        .maxRate(new BigDecimal("3.20"))
                        .minAmount(100_000L)
                        .maxAmount(10_000_000L)
                        .amountUnit(10_000L)
                        .minTermMonths((short) 6)
                        .maxTermMonths((short) 36)
                        .interestPayType(InterestPayType.SIMPLE)
                        .saleStatus(SaleStatus.ON_SALE)
                        .saleStartDate(LocalDate.of(2026, 1, 1))
                        .saleEndDate(LocalDate.of(2026, 12, 31))
                        .newFlag(false)
                        .singleAccountLimit(false)
                        .build())
                .getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(id, (short) TERM_MONTHS))
                .rate(new BigDecimal("3.20"))
                .build());
        new AccountNumberSequenceTestFixture(jdbcTemplate)
                .resetProductAccountSequence(
                        id,
                        AccountType.INSTALLMENT_SAVINGS,
                        AccountNumberSequenceTestFixture.INSTALLMENT_SAVINGS_PREFIX,
                        0L);
        return id;
    }

    private Long seedWithdrawalAccount() {
        return accountJpaRepository
                .save(AccountJpaEntity.builder()
                        .accountNumber("110000009902")
                        .customerId(customerId)
                        .accountType(AccountType.DEMAND_DEPOSIT)
                        .balance(10_000_000L)
                        .status(AccountStatus.ACTIVE)
                        .passwordHash(passwordEncoder.encode(ACCOUNT_PASSWORD))
                        .withdrawalRegistered(true)
                        .withdrawalRegisteredAt(LocalDateTime.now())
                        .openedDate(LocalDateTime.now())
                        .build())
                .getAccountId();
    }
}
