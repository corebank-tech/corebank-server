package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaEntity;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// #256: 1인1계좌 제한 판정(existsActiveSubscription)이 락 없는 SELECT라 동시 가입 요청 시
// 두 트랜잭션 모두 통과할 수 있었다(TOCTOU). ProductSubscriptionJpaRepository에 추가한
// PESSIMISTIC_WRITE 락 조회가 실제로 동시 요청을 직렬화하는지 검증한다.
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
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<ExecutionOutcome> first = submitExecute(startLatch);
        Future<ExecutionOutcome> second = submitExecute(startLatch);

        // 두 요청이 같은 시점에 execute()를 시작
        startLatch.countDown();

        List<ExecutionOutcome> outcomes = List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS));

        long successCount = outcomes.stream().filter(outcome -> outcome.result() != null).count();
        long rejectedCount = outcomes.stream().filter(outcome -> outcome.exception() != null).count();

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
                Long.class, productId, customerId);
        assertThat(subscriptionCount).isEqualTo(1L);

        // 패자 스레드가 판정에서 막혀 계좌개설까지 진행하지 못했는지 확인(락이 계좌개설보다 앞서 걸림)
        Long openedAccountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE customer_id = ? AND account_id != ?",
                Long.class, customerId, withdrawalAccountId);
        assertThat(openedAccountCount).isEqualTo(1L);
    }

    private Future<ExecutionOutcome> submitExecute(CountDownLatch startLatch) {
        return executor.submit(() -> {
            startLatch.await();
            try {
                ProductSubscriptionExecuteResult result =
                        productSubscriptionExecuteUseCase.execute(buildCommand());
                return new ExecutionOutcome(result, null);
            } catch (BusinessException exception) {
                return new ExecutionOutcome(null, exception);
            }
        });
    }

    private ProductSubscriptionExecuteCommand buildCommand() {
        return new ProductSubscriptionExecuteCommand(
                customerId, productId, 500_000L, 12, withdrawalAccountId,
                "1234", "1234", "ACC_PWD_test", "OTP_AUTH_test",
                List.of());
    }

    private Long seedSingleAccountLimitProduct() {
        Long id = productJpaRepository.save(ProductJpaEntity.builder()
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
                .build()).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(id, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        new AccountNumberSequenceTestFixture(jdbcTemplate).resetProductAccountSequence(
                id, AccountType.INSTALLMENT_SAVINGS,
                AccountNumberSequenceTestFixture.INSTALLMENT_SAVINGS_PREFIX, 0L);
        return id;
    }

    private Long seedWithdrawalAccount() {
        return accountJpaRepository.save(AccountJpaEntity.builder()
                .accountNumber("110000009901")
                .customerId(customerId)
                .accountType(AccountType.DEMAND_DEPOSIT)
                .balance(10_000_000L)
                .status(AccountStatus.ACTIVE)
                .passwordHash("x".repeat(60))
                .withdrawalRegistered(true)
                .withdrawalRegisteredAt(LocalDateTime.now())
                .openedDate(LocalDateTime.now())
                .build()).getAccountId();
    }

    private record ExecutionOutcome(ProductSubscriptionExecuteResult result, BusinessException exception) {
    }
}
