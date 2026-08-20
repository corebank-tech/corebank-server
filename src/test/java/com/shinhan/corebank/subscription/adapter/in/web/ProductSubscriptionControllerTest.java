package com.shinhan.corebank.subscription.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaEntity;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTermsJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTermsJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTermsJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaRepository;
import com.shinhan.corebank.subscription.adapter.out.persistence.SubscriptionTestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class ProductSubscriptionControllerTest extends IntegrationTestSupport {


    @Autowired
    MockMvc mockMvc;
    @Autowired
    ProductJpaRepository productJpaRepository;
    @Autowired
    ProductRateTierJpaRepository rateTierRepository;
    @Autowired
    ProductTermsJpaRepository termsRepository;
    @Autowired
    AccountJpaRepository accountJpaRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    CustomerTestFixture customerTestFixture;
    @Autowired
    ProductSubscriptionJpaRepository subscriptionJpaRepository;
    @Autowired
    EntityManager entityManager;

    private final ObjectMapper jackson = new ObjectMapper();

    private Long customerId;

    // account.customer_id에 fk_account_customer 제약이 있어, 계좌를 심기 전에 customer 행부터
    // 만들어야 한다 — customer 도메인은 아직 JPA 엔티티가 없어 CustomerTestFixture(raw SQL)로 심는다.
    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
    }

    @Test
    void validate_success_deposit() throws Exception {
        Long productId = productJpaRepository.save(
                ProductTestFixtures.productWithCode("VAL-101")).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        Long accountId = seedAccount("110000000877", customerId, 10_000_000L);

        mockMvc.perform(post("/product-subscriptions/validation")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": %d,
                                  "subscriptionAmount": 6000000,
                                  "termMonths": 12,
                                  "withdrawalAccountId": %d,
                                  "agreedTerms": []
                                }
                                """.formatted(productId, accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.violations.length()").value(0))
                .andExpect(jsonPath("$.data.baseRate").value(3.20))
                .andExpect(jsonPath("$.data.withdrawalAccountNumber").value("110******877"));
    }

    @Test
    void validate_productNotFound() throws Exception {
        Long accountId = seedAccount("110000000878", customerId, 0L);

        mockMvc.perform(post("/product-subscriptions/validation")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"productId": 999999, "subscriptionAmount": 500000, "termMonths": 12, "withdrawalAccountId": %d, "agreedTerms": []}
                                """.formatted(accountId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0201"));
    }

    @Test
    void validate_accountNotOwnedByCustomer() throws Exception {
        Long productId = productJpaRepository.save(
                ProductTestFixtures.productWithCode("VAL-102")).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        Long otherCustomerId = customerTestFixture.createCustomer();
        Long otherCustomersAccountId = seedAccount("110000000879", otherCustomerId, 10_000_000L); // 로그인한 고객과 다름

        mockMvc.perform(post("/product-subscriptions/validation")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"productId": %d, "subscriptionAmount": 500000, "termMonths": 12, "withdrawalAccountId": %d, "agreedTerms": []}
                                """.formatted(productId, otherCustomersAccountId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACC0201"));
    }

    @Test
    void validate_missingRequiredTermsAgreement() throws Exception {
        Long productId = productJpaRepository.save(
                ProductTestFixtures.productWithCode("VAL-103")).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_DEPOSIT");
        termsRepository.save(ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(productId, termsId))
                .displayOrder((short) 1)
                .build());
        Long accountId = seedAccount("110000000880", customerId, 10_000_000L);

        mockMvc.perform(post("/product-subscriptions/validation")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"productId": %d, "subscriptionAmount": 6000000, "termMonths": 12, "withdrawalAccountId": %d, "agreedTerms": []}
                                """.formatted(productId, accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.violations[0].field").value("agreedTerms"))
                .andExpect(jsonPath("$.data.violations[0].code").value("PRD0003"));
    }

    @Test
    @DisplayName("정기예금 가입 결과를 전체 필드 보존한 채 200으로 응답하고 autoTransferPrefill은 없다")
    void getSubscriptionResult_deposit() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_dep");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000010", customerId, null);
        Long accountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000011", customerId, productId);
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId, accountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.subscriptionId").value(subscriptionId))
                .andExpect(jsonPath("$.data.accountId").value(accountId))
                .andExpect(jsonPath("$.data.accountNumber").value("110******011"))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productName").value("정기예금"))
                .andExpect(jsonPath("$.data.productGroup").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.subscriptionAmount").value(1_000_000))
                .andExpect(jsonPath("$.data.termMonths").value(12))
                .andExpect(jsonPath("$.data.appliedRate").value(2.80))
                .andExpect(jsonPath("$.data.openedDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.maturityDate").value("2027-08-01"))
                .andExpect(jsonPath("$.data.expectedMaturityAmount").value(1_028_000))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionNumber").value("20260801000000000001"))
                .andExpect(jsonPath("$.data.subscribedAt").value("2026-08-01T10:30:00"))
                .andExpect(jsonPath("$.data.autoTransferPrefill").doesNotExist());
    }

    @Test
    @DisplayName("정기적금 가입 결과는 autoTransferPrefill이 마스킹 없는 실제 계좌번호로 채워진다")
    void getSubscriptionResult_savings_includesAutoTransferPrefill() throws Exception {
        ProductJpaEntity savingsProduct = productJpaRepository.save(ProductJpaEntity.builder()
                .productCode("CTL-SAV-01")
                .productName("청년 희망 적금")
                .productGroup(ProductGroup.SAVINGS)
                .depositType(DepositType.INSTALLMENT)
                .baseRate(new BigDecimal("3.20"))
                .maxRate(new BigDecimal("4.50"))
                .minAmount(10_000L)
                .maxAmount(10_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(SaleStatus.ON_SALE)
                .saleEndDate(LocalDate.of(2026, 12, 31))
                .newFlag(false)
                .singleAccountLimit(false)
                .build());
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_sav");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000020", customerId, null);
        Long accountId = SubscriptionTestFixtures.insertAccount(
                jdbcTemplate, "110000000021", customerId, savingsProduct.getProductId());
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(
                        customerId, savingsProduct.getProductId(), withdrawalAccountId, accountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productGroup").value("SAVINGS"))
                .andExpect(jsonPath("$.data.autoTransferPrefill.withdrawalAccountId").value(withdrawalAccountId))
                .andExpect(jsonPath("$.data.autoTransferPrefill.depositAccountNumber").value("110000000021"))
                .andExpect(jsonPath("$.data.autoTransferPrefill.amount").value(1_000_000))
                .andExpect(jsonPath("$.data.autoTransferPrefill.cycleMonths").value(1))
                .andExpect(jsonPath("$.data.autoTransferPrefill.endDate").value("2027-08-01"));
    }

    @Test
    @DisplayName("계좌 미개설(가입 실패) 적금 건은 autoTransferPrefill을 내려주지 않는다")
    void getSubscriptionResult_savings_withoutAccount_noAutoTransferPrefill() throws Exception {
        ProductJpaEntity savingsProduct = productJpaRepository.save(ProductJpaEntity.builder()
                .productCode("CTL-SAV-02")
                .productName("청년 희망 적금")
                .productGroup(ProductGroup.SAVINGS)
                .depositType(DepositType.INSTALLMENT)
                .baseRate(new BigDecimal("3.20"))
                .maxRate(new BigDecimal("4.50"))
                .minAmount(10_000L)
                .maxAmount(10_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(SaleStatus.ON_SALE)
                .saleEndDate(LocalDate.of(2026, 12, 31))
                .newFlag(false)
                .singleAccountLimit(false)
                .build());
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_sav_noacc");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000050", customerId, null);
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(customerId, savingsProduct.getProductId(), withdrawalAccountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productGroup").value("SAVINGS"))
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.autoTransferPrefill").doesNotExist());
    }

    @Test
    @DisplayName("계좌 개설 전(가입 실패) 건은 accountId/accountNumber가 null로 응답한다")
    void getSubscriptionResult_withoutAccount_returnsNullAccountFields() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_noacc");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000030", customerId, null);
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.accountNumber").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 가입건이면 404 + PRD0203을 반환한다")
    void getSubscriptionResult_notFound() throws Exception {
        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", 999_999L)
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0203"));
    }

    @Test
    @DisplayName("타인 소유 가입건이면 404 + PRD0203을 반환한다(존재 여부 비노출)")
    void getSubscriptionResult_otherCustomer_returnsNotFound() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long ownerCustomerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_owner");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000040", ownerCustomerId, null);
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(ownerCustomerId, productId, withdrawalAccountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(999_888L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0203"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getSubscriptionResult_unauthenticated() throws Exception {
        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("subscriptionId가 0이면 400 + CMN0001을 반환한다")
    void getSubscriptionResult_zeroId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", 0L)
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("subscriptionId가 음수면 400 + CMN0001을 반환한다")
    void getSubscriptionResult_negativeId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", -1L)
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("정기적금 가입 실행 성공 시 계좌가 개설되고 가입 건이 SUCCESS로 저장된다")
    void execute_success_createsAccountAndSubscription() throws Exception {
        Long productId = seedSavingsProduct("EXE-101", false);
        Long withdrawalAccountId = seedAccount("110000009001", customerId, 10_000_000L);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionNumber").doesNotExist())
                .andExpect(jsonPath("$.data.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.appliedRate").value(3.20));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 재요청하면 신규 가입 없이 최초 응답을 재생한다")
    void execute_sameIdempotencyKey_returnsFirstResponseWithoutDuplicateSubscription() throws Exception {
        Long productId = seedSavingsProduct("EXE-102", false);
        Long withdrawalAccountId = seedAccount("110000009002", customerId, 10_000_000L);
        String idempotencyKey = UUID.randomUUID().toString();
        String requestJson = executeRequestJson(productId, withdrawalAccountId, 500_000L, 12);

        String first = mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 같은 테스트 트랜잭션 안에서 순차 호출하면 idempotency_key 행이 1차 호출 때 로드된 채로
        // 영속성 컨텍스트에 남아있어, complete()의 벌크 UPDATE(@Modifying) 결과가 반영 안 된 stale
        // 상태로 재조회될 수 있다 — flush+clear로 비운다.
        entityManager.flush();
        entityManager.clear();

        String second = mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // MySQL의 JSON 컬럼(idempotency_key.response_snapshot)은 저장 시 숫자를 정규화해서
        // 소수 끝자리 0을 지울 수 있다("3.20" -> "3.2") — 바이트 단위 비교 대신 JsonNode로
        // 구조적으로 비교한다.
        assertThat(jackson.readTree(second)).isEqualTo(jackson.readTree(first));

        Long subscriptionId = jackson.readTree(first).get("data").get("subscriptionId").asLong();
        assertThat(subscriptionJpaRepository.count()).isEqualTo(1);
        assertThat(subscriptionJpaRepository.findById(subscriptionId)).isPresent();
    }

    @Test
    @DisplayName("정기예금은 아직 지원하지 않아 400 + CMN0001을 반환한다")
    void execute_timeDeposit_returnsInvalidInput() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.productWithCode("EXE-103")).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        Long withdrawalAccountId = seedAccount("110000009003", customerId, 10_000_000L);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("1인1계좌 제한 상품에 이미 가입한 이력이 있으면 409 + PRD0301을 반환한다")
    void execute_alreadySubscribedSingleAccountLimitProduct_returns409() throws Exception {
        Long productId = seedSavingsProduct("EXE-104", true);
        Long withdrawalAccountId = seedAccount("110000009004", customerId, 10_000_000L);
        subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(customerId, productId, withdrawalAccountId));

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRD0301"));
    }

    @Test
    @DisplayName("신규 계좌 비밀번호와 확인값이 다르면 400 + APW0002를 반환한다")
    void execute_newPasswordConfirmMismatch_returnsApw0002() throws Exception {
        Long productId = seedSavingsProduct("EXE-105", false);
        Long withdrawalAccountId = seedAccount("110000009005", customerId, 10_000_000L);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": %d,
                                  "subscriptionAmount": 500000,
                                  "termMonths": 12,
                                  "withdrawalAccountId": %d,
                                  "newAccountPassword": "1234",
                                  "newAccountPasswordConfirm": "9999",
                                  "accountPasswordAuthToken": "ACC_PWD_test",
                                  "otpAuthToken": "OTP_AUTH_test",
                                  "agreedTerms": []
                                }
                                """.formatted(productId, withdrawalAccountId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APW0002"));
    }

    private Long seedSavingsProduct(String productCode, boolean singleAccountLimit) {
        Long productId = productJpaRepository.save(ProductJpaEntity.builder()
                .productCode(productCode)
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
                .singleAccountLimit(singleAccountLimit)
                .build()).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        new AccountNumberSequenceTestFixture(jdbcTemplate).resetProductAccountSequence(
                productId, AccountType.INSTALLMENT_SAVINGS,
                AccountNumberSequenceTestFixture.INSTALLMENT_SAVINGS_PREFIX, 0L);
        return productId;
    }

    private String executeRequestJson(Long productId, Long withdrawalAccountId, Long amount, int termMonths) {
        return """
                {
                  "productId": %d,
                  "subscriptionAmount": %d,
                  "termMonths": %d,
                  "withdrawalAccountId": %d,
                  "newAccountPassword": "1234",
                  "newAccountPasswordConfirm": "1234",
                  "accountPasswordAuthToken": "ACC_PWD_test",
                  "otpAuthToken": "OTP_AUTH_test",
                  "agreedTerms": []
                }
                """.formatted(productId, amount, termMonths, withdrawalAccountId);
    }

    private Long seedAccount(String accountNumber, Long customerId, long balance) {
        return accountJpaRepository.save(AccountJpaEntity.builder()
                .accountNumber(accountNumber)
                .customerId(customerId)
                .accountType(AccountType.DEMAND_DEPOSIT)
                .balance(balance)
                .status(AccountStatus.ACTIVE)
                .passwordHash("x".repeat(60))
                .withdrawalRegistered(true)
                .withdrawalRegisteredAt(LocalDateTime.now())
                .openedDate(LocalDateTime.now())
                .build()).getAccountId();
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
