package com.shinhan.corebank.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaEntity;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaRepository;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteCommand;
import com.shinhan.corebank.subscription.application.port.out.SaveTermsAgreementPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// ProductSubscriptionControllerTest는 클래스 레벨 @Transactional을 쓰는데, SaveTermsAgreementPort를
// @MockitoBean으로 갈아끼우면 그 클래스의 모든 테스트(성공 케이스 포함)가 약관동의 저장을 건너뛰게
// 된다 — 그래서 이 롤백 검증만 별도 클래스로 분리하고, ProductAccountOpeningRollbackTest와 같은
// 패턴(클래스 레벨 @Transactional 없이 UseCase를 직접 호출 + @AfterEach 수동 정리)을 따른다.
class ProductSubscriptionExecuteRollbackTest extends IntegrationTestSupport {

    private static final String PRODUCT_CODE = "EXE-ROLLBACK-01";
    private static final String DEPOSIT_PRODUCT_CODE = "EXE-ROLLBACK-02";

    @Autowired
    private ProductSubscriptionExecuteUseCase productSubscriptionExecuteUseCase;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductRateTierJpaRepository rateTierRepository;

    @Autowired
    private ProductSubscriptionJpaRepository subscriptionJpaRepository;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @MockitoBean
    private SaveTermsAgreementPort saveTermsAgreementPort;

    // 상품가입 실행은 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    private Long customerId;
    private Long productId;
    private Long withdrawalAccountId;

    @AfterEach
    void tearDown() {
        if (productId != null) {
            AccountNumberSequenceTestFixture sequenceFixture = new AccountNumberSequenceTestFixture(jdbcTemplate);
            sequenceFixture.deleteProductAccountSequence(productId, AccountType.INSTALLMENT_SAVINGS);
            sequenceFixture.deleteProductAccountSequence(productId, AccountType.TIME_DEPOSIT);
            jdbcTemplate.update("DELETE FROM product_rate_tier WHERE product_id = ?", productId);
            jdbcTemplate.update("DELETE FROM account WHERE product_id = ?", productId);
        }
        if (withdrawalAccountId != null) {
            jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", withdrawalAccountId);
        }
        if (productId != null) {
            jdbcTemplate.update("DELETE FROM product WHERE product_id = ?", productId);
        }
        if (customerId != null) {
            customerTestFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("계좌개설 이후 단계(약관동의 저장)가 실패하면 계좌개설·가입저장까지 전량 롤백된다")
    void execute_termsAgreementSaveFails_rollsBackAccountAndSubscription() {
        customerId = customerTestFixture.createCustomer();
        productId = seedSavingsProduct();
        withdrawalAccountId = seedWithdrawalAccount();

        doThrow(new RuntimeException("forced terms agreement save failure"))
                .when(saveTermsAgreementPort)
                .saveAll(anyList());

        long accountCountBefore = countAccountsByCustomer();
        long subscriptionCountBefore = subscriptionJpaRepository.count();

        ProductSubscriptionExecuteCommand command = new ProductSubscriptionExecuteCommand(
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

        Throwable thrown = catchThrowable(() -> productSubscriptionExecuteUseCase.execute(command));

        assertThat(thrown)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("forced terms agreement save failure");
        assertThat(countAccountsByCustomer()).isEqualTo(accountCountBefore);
        assertThat(subscriptionJpaRepository.count()).isEqualTo(subscriptionCountBefore);
    }

    @Test
    @DisplayName("정기예금 가입에서 초입금 기표 이후 단계가 실패하면 출금계좌 잔액과 원장까지 전량 롤백된다")
    void execute_timeDeposit_termsAgreementSaveFails_rollsBackInitialDeposit() {
        customerId = customerTestFixture.createCustomer();
        productId = seedDepositProduct();
        withdrawalAccountId = seedWithdrawalAccount();

        doThrow(new RuntimeException("forced terms agreement save failure"))
                .when(saveTermsAgreementPort)
                .saveAll(anyList());

        long accountCountBefore = countAccountsByCustomer();
        long subscriptionCountBefore = subscriptionJpaRepository.count();
        long ledgerCountBefore = countProductSubscriptionLedgerEntries();

        ProductSubscriptionExecuteCommand command = new ProductSubscriptionExecuteCommand(
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

        Throwable thrown = catchThrowable(() -> productSubscriptionExecuteUseCase.execute(command));

        assertThat(thrown)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("forced terms agreement save failure");
        assertThat(countAccountsByCustomer()).isEqualTo(accountCountBefore);
        assertThat(subscriptionJpaRepository.count()).isEqualTo(subscriptionCountBefore);
        assertThat(countProductSubscriptionLedgerEntries()).isEqualTo(ledgerCountBefore);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT balance FROM account WHERE account_id = ?", Long.class, withdrawalAccountId))
                .isEqualTo(10_000_000L);
    }

    private long countProductSubscriptionLedgerEntries() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id = ? AND transaction_type = 'PRODUCT_SUBSCRIPTION'",
                Long.class,
                withdrawalAccountId);
    }

    private Long seedDepositProduct() {
        Long id = productJpaRepository
                .save(ProductTestFixtures.productWithCode(DEPOSIT_PRODUCT_CODE))
                .getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(id, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        new AccountNumberSequenceTestFixture(jdbcTemplate)
                .resetProductAccountSequence(
                        id, AccountType.TIME_DEPOSIT, AccountNumberSequenceTestFixture.TIME_DEPOSIT_PREFIX, 0L);
        return id;
    }

    private long countAccountsByCustomer() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE customer_id = ? AND account_id != ?",
                Long.class,
                customerId,
                withdrawalAccountId);
    }

    private Long seedSavingsProduct() {
        Long id = productJpaRepository
                .save(ProductJpaEntity.builder()
                        .productCode(PRODUCT_CODE)
                        .productName("청년 희망 적금")
                        .productGroup(ProductGroup.SAVINGS)
                        .depositType(DepositType.INSTALLMENT)
                        .summary("테스트용 적금")
                        .description("테스트용 적금 설명")
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
                        .accountNumber("110000009900")
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
}
