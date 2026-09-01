package com.shinhan.corebank.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaEntity;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// #256: 1인1계좌 제한 판정(existsActiveSubscription)이 락 없는 SELECT라 동시 가입 요청 시
// 두 트랜잭션 모두 통과할 수 있었다(TOCTOU). ProductLockJpaRepository의 product 행
// PESSIMISTIC_WRITE 락과 뒤이은 product_subscription 잠금 조회가 실제로 동시 요청을
// 직렬화하는지 검증한다.
@DisplayName("상품가입 1인1계좌 제한 동시성 통합 테스트")
class ProductSubscriptionExecuteConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final String PRODUCT_CODE = "CONCUR-SGL-01";

    @Autowired
    private ProductSubscriptionExecuteUseCase productSubscriptionExecuteUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductRateTierJpaRepository rateTierRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private DataSource dataSource;

    // 상품가입 실행은 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    private ExecutorService executor;
    private Long customerId;
    private Long productId;
    private Long withdrawalAccountId;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
        productId = seedSingleAccountLimitProduct();
        withdrawalAccountId = seedWithdrawalAccount();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        jdbcTemplate.update("DELETE FROM product_subscription WHERE product_id = ?", productId);
        new AccountNumberSequenceTestFixture(jdbcTemplate)
                .deleteProductAccountSequence(productId, AccountType.INSTALLMENT_SAVINGS);
        jdbcTemplate.update("DELETE FROM product_rate_tier WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM account WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", withdrawalAccountId);
        jdbcTemplate.update("DELETE FROM product WHERE product_id = ?", productId);
        customerTestFixture.deleteCustomer(customerId);
    }

    @Test
    @DisplayName("1인1계좌 제한 상품에 동시에 두 번 가입 요청을 보내면 하나만 성공하고 나머지는 PRD0301로 거부된다")
    void execute_concurrentSubscriptionToSingleAccountLimitProduct_onlyOneSucceeds() throws Exception {
        // 출발선만 래치로 맞추면 한쪽이 먼저 끝난 뒤 다른 쪽이 시작하는 실행 순서에서도 단정문이
        // 통과한다(잠금 조회가 이미 커밋된 행을 보므로) — 즉 락을 지운 회귀를 놓칠 수 있다.
        // 그래서 테스트가 먼저 product 행 락을 선점해 두 요청을 락 앞에 나란히 세워두고, 둘 다
        // 막혀 있는 것을 확인한 뒤에 놓아준다. 락이 풀리는 순간부터는 두 트랜잭션이 서로 경합한다.
        CountDownLatch anyFinished = new CountDownLatch(1);
        List<ExecutionOutcome> outcomes;

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lockProduct =
                    blocker.prepareStatement("SELECT product_id FROM product WHERE product_id = ? FOR UPDATE")) {
                lockProduct.setLong(1, productId);
                lockProduct.executeQuery();
            }

            Future<ExecutionOutcome> first = submitExecute(anyFinished);
            Future<ExecutionOutcome> second = submitExecute(anyFinished);

            // 판정이 직렬화된다면 둘 다 product 행 락 앞에서 막혀 있어야 한다.
            // 하나라도 이 사이에 끝나면 락 없이 판정을 통과했다는 뜻이다.
            assertThat(anyFinished.await(1, TimeUnit.SECONDS)).isFalse();

            blocker.rollback();

            outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }

        long successCount =
                outcomes.stream().filter(outcome -> outcome.result() != null).count();
        long rejectedCount =
                outcomes.stream().filter(outcome -> outcome.exception() != null).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(1);

        BusinessException rejected = outcomes.stream()
                .map(ExecutionOutcome::exception)
                .filter(exception -> exception != null)
                .findFirst()
                .orElseThrow();
        assertThat(rejected.getErrorCode()).isEqualTo(SubscriptionErrorCode.ALREADY_SUBSCRIBED);

        Long subscriptionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_subscription WHERE product_id = ? AND customer_id = ?",
                Long.class,
                productId,
                customerId);
        assertThat(subscriptionCount).isEqualTo(1L);

        // 패자 스레드가 판정에서 막혀 계좌개설까지 진행하지 못했는지 확인(락이 계좌개설보다 앞서 걸림)
        Long openedAccountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE customer_id = ? AND account_id != ?",
                Long.class,
                customerId,
                withdrawalAccountId);
        assertThat(openedAccountCount).isEqualTo(1L);
    }

    private Future<ExecutionOutcome> submitExecute(CountDownLatch anyFinished) {
        return executor.submit(() -> {
            try {
                ProductSubscriptionExecuteResult result = productSubscriptionExecuteUseCase.execute(buildCommand());
                return new ExecutionOutcome(result, null);
            } catch (BusinessException exception) {
                return new ExecutionOutcome(null, exception);
            } finally {
                anyFinished.countDown();
            }
        });
    }

    private ProductSubscriptionExecuteCommand buildCommand() {
        return new ProductSubscriptionExecuteCommand(
                customerId,
                productId,
                500_000L,
                12,
                withdrawalAccountId,
                "1234",
                "1234",
                "ACC_PWD_test",
                "OTP_AUTH_test",
                List.of());
    }

    private Long seedSingleAccountLimitProduct() {
        Long id = productJpaRepository
                .save(ProductJpaEntity.builder()
                        .productCode(PRODUCT_CODE)
                        .productName("1인1계좌 제한 테스트 적금")
                        .productGroup(ProductGroup.SAVINGS)
                        .depositType(DepositType.INSTALLMENT)
                        .summary("동시성 테스트용 적금")
                        .description("동시성 테스트용 적금 설명")
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
                        .singleAccountLimit(true)
                        .build())
                .getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(id, (short) 12))
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
                        .accountNumber("110000009901")
                        .customerId(customerId)
                        .accountType(AccountType.DEMAND_DEPOSIT)
                        .balance(10_000_000L)
                        .status(AccountStatus.ACTIVE)
                        .passwordHash("x".repeat(60))
                        .withdrawalRegistered(true)
                        .withdrawalRegisteredAt(LocalDateTime.now())
                        .openedDate(LocalDateTime.now())
                        .build())
                .getAccountId();
    }

    private record ExecutionOutcome(ProductSubscriptionExecuteResult result, BusinessException exception) {}
}
