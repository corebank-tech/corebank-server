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
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.domain.exception.OtpErrorCode;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductPreferentialRateJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductPreferentialRateJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductPreferentialRateJpaRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    ProductPreferentialRateJpaRepository preferentialRateRepository;
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

    // 상품가입 실행은 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    OtpAuthTokenVerifier otpAuthTokenVerifier;

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
    @DisplayName("가입 건에 연결된 계좌가 타 고객 소유면 PRD9003을 반환한다")
    void getSubscriptionResult_accountOwnedByOtherCustomer_returnsAccountNotFound() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long customerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_owner");
        Long otherCustomerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_other");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000031", customerId, null);
        Long otherCustomerAccountId =
                SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000032", otherCustomerId, productId);
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(
                        customerId, productId, withdrawalAccountId, otherCustomerAccountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PRD9003"));
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
        long withdrawalBalanceBefore = balanceOf(withdrawalAccountId);

        String response = mockMvc.perform(post("/product-subscriptions")
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
                .andExpect(jsonPath("$.data.appliedRate").value(3.20))
                .andReturn().getResponse().getContentAsString();

        // REQ-PRDT-012 인수기준: "적금 가입 직후 신규 계좌 잔액이 0원이고 출금계좌 잔액이 변동하지
        // 않는다". transactionNumber 부재만으로는 기표가 없었다는 것만 알 뿐, 잔액이 실제로 그대로인지는
        // 확인되지 않는다 — 두 잔액을 직접 읽어 단언한다.
        Long newAccountId = jackson.readTree(response).get("data").get("accountId").asLong();
        assertThat(balanceOf(newAccountId)).isZero();
        assertThat(balanceOf(withdrawalAccountId)).isEqualTo(withdrawalBalanceBefore);
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
    @DisplayName("정기예금 가입 실행 시 초입금이 기표되어 출금계좌 잔액이 줄고 신규 계좌에 반영된다")
    void execute_timeDeposit_movesInitialDepositAndRecordsLedger() throws Exception {
        Long productId = seedDepositProduct("EXE-103");
        Long withdrawalAccountId = seedAccount("110000009003", customerId, 10_000_000L);

        String response = mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productGroup").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.transactionNumber").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = jackson.readTree(response).get("data");
        Long newAccountId = data.get("accountId").asLong();
        String transactionNumber = data.get("transactionNumber").asText();

        // 잔액 변경은 AccountLockJpaEntity(부분 매핑)로 이뤄져 AccountJpaEntity 캐시에 반영되지
        // 않는다 — DB 실제 값을 봐야 하므로 flush 후 JdbcTemplate으로 직접 조회한다.
        entityManager.flush();
        entityManager.clear();

        assertThat(balanceOf(withdrawalAccountId)).isEqualTo(9_500_000L);
        assertThat(balanceOf(newAccountId)).isEqualTo(500_000L);

        assertThat(jdbcTemplate.queryForList(
                """
                SELECT account_id, direction, amount, balance_after, transaction_type, transfer_id
                  FROM ledger_entry
                 WHERE transaction_number = ?
                 ORDER BY direction
                """,
                transactionNumber))
                .hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.get("transaction_type")).isEqualTo("PRODUCT_SUBSCRIPTION");
                    assertThat(row.get("transfer_id")).isNull();
                    assertThat(row.get("amount")).isEqualTo(500_000L);
                })
                .anySatisfy(row -> {
                    assertThat(row.get("direction")).isEqualTo("DEPOSIT");
                    assertThat(row.get("account_id")).isEqualTo(newAccountId);
                    assertThat(row.get("balance_after")).isEqualTo(500_000L);
                })
                .anySatisfy(row -> {
                    assertThat(row.get("direction")).isEqualTo("WITHDRAWAL");
                    assertThat(row.get("account_id")).isEqualTo(withdrawalAccountId);
                    assertThat(row.get("balance_after")).isEqualTo(9_500_000L);
                });

        assertThat(subscriptionJpaRepository.findById(data.get("subscriptionId").asLong())
                .orElseThrow().getTransactionNumber()).isEqualTo(transactionNumber);
    }

    @Test
    @DisplayName("정기예금 가입 시 출금계좌 잔액이 부족하면 400 + LMT0001을 반환하고 계좌가 개설되지 않는다")
    void execute_timeDeposit_insufficientBalance_returnsLmt0001() throws Exception {
        Long productId = seedDepositProduct("EXE-108");
        Long withdrawalAccountId = seedAccount("110000009008", customerId, 100_000L);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LMT0001"));

        entityManager.flush();
        entityManager.clear();

        assertThat(balanceOf(withdrawalAccountId)).isEqualTo(100_000L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE product_id = ?", Long.class, productId)).isZero();
        assertThat(subscriptionJpaRepository.count()).isZero();
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

    // toBusinessException()이 이번에 새로 짠 코드다 — SubscriptionViolation(#68이 이미 코드를
    // 들고 있음)을 문자열 매칭 없이 그대로 감싸서 던지는데, 이 경로를 지나가는 테스트가 지금까지
    // 하나도 없었다. amountOutOfRange 하나만 대표로 확인하면 나머지 violation 코드(PRD0002~0006)도
    // 같은 매핑 함수를 타므로 메커니즘 자체는 충분히 검증된다.
    @Test
    @DisplayName("사전검증 위반(가입금액 범위 초과)이 있으면 실행 API도 400 + PRD0001을 반환한다")
    void execute_amountOutOfRange_returnsPrd0001() throws Exception {
        Long productId = seedSavingsProduct("EXE-108", false);
        Long withdrawalAccountId = seedAccount("110000009008", customerId, 10_000_000L);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        // seedSavingsProduct의 maxAmount(10,000,000)를 초과 — amountUnit(10,000)의
                        // 배수라 AMOUNT_UNIT_MISMATCH는 안 겹치고 AMOUNT_OUT_OF_RANGE만 걸린다
                        .content(executeRequestJson(productId, withdrawalAccountId, 50_000_000L, 12)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRD0001"));
    }

    // 상품에 연결되지 않은(존재하지 않는) termsId로 동의를 보내면 계좌개설 이전에 404 + PRD0202로
    // 걸러야 한다 — 걸러지지 않으면 계좌개설·가입저장까지 끝낸 뒤 약관동의 저장 단계의 FK
    // 위반으로 훨씬 늦게(5xx로) 실패한다(코드리뷰에서 지적된 문제, ProductSubscriptionValidationService
    // .validateTerms()에서 역방향 검증을 추가해 수정).
    @Test
    @DisplayName("상품에 연결되지 않은 termsId로 동의를 보내면 404 + PRD0202를 반환한다")
    void execute_agreedTermsWithUnknownTermsId_returnsPrd0202() throws Exception {
        Long productId = seedSavingsProduct("EXE-110", false);
        Long withdrawalAccountId = seedAccount("110000009010", customerId, 10_000_000L);

        String requestJson = """
                {
                  "productId": %d,
                  "subscriptionAmount": 500000,
                  "termMonths": 12,
                  "withdrawalAccountId": %d,
                  "newAccountPassword": "1234",
                  "newAccountPasswordConfirm": "1234",
                  "accountPasswordAuthToken": "ACC_PWD_test",
                  "otpAuthToken": "OTP_AUTH_test",
                  "agreedTerms": [{"termsId": 999999999, "version": "v1.0"}]
                }
                """.formatted(productId, withdrawalAccountId);

        long accountCountBefore = accountJpaRepository.count();

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0202"));

        // 검증 단계에서 걸러졌으므로 계좌개설까지 가지 않았어야 한다
        assertThat(accountJpaRepository.count()).isEqualTo(accountCountBefore);
    }

    // 실행 API는 satisfiedConditionCodes를 요청 필드로 받지 않는다(ProductSubscriptionExecuteRequest에
    // 없음) — 클라이언트가 신고한 우대조건을 그대로 믿고 실제 가입에 반영하면 안 되기 때문이다
    // (PR #147 합의, 코드리뷰 지적으로 재확인). Jackson은 기본적으로 알 수 없는 JSON 필드를 무시하므로,
    // 이 필드를 요청 본문에 억지로 끼워 넣어도 조용히 버려지고 적용금리는 기본금리만 반영돼야 한다.
    @Test
    @DisplayName("실행 요청에 satisfiedConditionCodes를 끼워 넣어도 무시되고 기본금리만 적용된다")
    void execute_ignoresClientReportedSatisfiedConditionCodes() throws Exception {
        Long productId = seedSavingsProduct("EXE-111", false);
        Long withdrawalAccountId = seedAccount("110000009011", customerId, 10_000_000L);
        preferentialRateRepository.save(ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(new ProductPreferentialRateJpaEntityId(productId, "AUTO_TRANSFER"))
                .conditionName("자동이체 6회 이상")
                .rate(new BigDecimal("0.50"))
                .build());

        String requestJson = """
                {
                  "productId": %d,
                  "subscriptionAmount": 500000,
                  "termMonths": 12,
                  "withdrawalAccountId": %d,
                  "newAccountPassword": "1234",
                  "newAccountPasswordConfirm": "1234",
                  "accountPasswordAuthToken": "ACC_PWD_test",
                  "otpAuthToken": "OTP_AUTH_test",
                  "agreedTerms": [],
                  "satisfiedConditionCodes": ["AUTO_TRANSFER"]
                }
                """.formatted(productId, withdrawalAccountId);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isOk())
                // seedSavingsProduct의 rate tier는 base 3.20 — AUTO_TRANSFER(0.50)가 반영됐다면 3.70이 됐을 것
                .andExpect(jsonPath("$.data.appliedRate").value(3.20));
    }

    // fingerprint()에 newAccountPassword를 빠뜨렸던 실수를 실제로 한 번 저지른 적이 있어서
    // (이전 리뷰에서 발견·수정) 회귀 가드로 남긴다 — 이 필드가 다시 빠지면 다른 비밀번호로 보낸
    // 재요청이 조용히 최초 응답으로 재생돼버리는데, 그건 단순 버그가 아니라 보안적으로 의미 있는
    // 사고(요청 내용이 다른데 같은 요청으로 취급)라 일반적인 CMN0302 테스트보다 이 필드를 직접 겨냥한다.
    @Test
    @DisplayName("같은 Idempotency-Key인데 신규 계좌 비밀번호가 다르면 재생 대신 409 + CMN0302를 반환한다")
    void execute_sameIdempotencyKeyDifferentPassword_returnsConflict() throws Exception {
        Long productId = seedSavingsProduct("EXE-109", false);
        Long withdrawalAccountId = seedAccount("110000009009", customerId, 10_000_000L);
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String differentPasswordJson = """
                {
                  "productId": %d,
                  "subscriptionAmount": 500000,
                  "termMonths": 12,
                  "withdrawalAccountId": %d,
                  "newAccountPassword": "9999",
                  "newAccountPasswordConfirm": "9999",
                  "accountPasswordAuthToken": "ACC_PWD_test",
                  "otpAuthToken": "OTP_AUTH_test",
                  "agreedTerms": []
                }
                """.formatted(productId, withdrawalAccountId);

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(differentPasswordJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    // "계좌개설 이후 단계(약관동의 저장) 실패 시 전량 롤백" 검증은
    // ProductSubscriptionExecuteRollbackTest로 옮겼다 — SaveTermsAgreementPort를 @MockitoBean으로
    // 갈아끼우면 이 클래스의 다른 테스트(약관동의 저장을 실제로 검증하는 성공 케이스 포함)까지
    // 전부 no-op 처리돼버리기 때문에, 별도 클래스(ProductAccountOpeningRollbackTest와 동일 패턴)로
    // 분리했다.

    // REQ-PRDT-010 인수기준: "인증 없이 가입 실행 API 호출 시 거부된다".
    // 이 클래스의 나머지 테스트는 OtpAuthTokenVerifier를 no-op mock으로 두고 전부 통과시키므로,
    // 검증이 실패하는 경로는 여기서만 탄다 — 이 테스트가 없으면 OTP 검증 호출 자체가 사라져도
    // 스위트 전체가 초록이다.
    @Test
    @DisplayName("OTP 인증 토큰이 유효하지 않으면 403 + OTP0101을 반환하고 계좌가 개설되지 않는다")
    void execute_invalidOtpAuthToken_returnsOtp0101() throws Exception {
        Long productId = seedSavingsProduct("EXE-112", false);
        Long withdrawalAccountId = seedAccount("110000009012", customerId, 10_000_000L);
        doThrow(new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN))
                .when(otpAuthTokenVerifier).verifyAndConsume(any());

        long accountCountBefore = accountJpaRepository.count();
        long subscriptionCountBefore = subscriptionJpaRepository.count();

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJson(productId, withdrawalAccountId, 500_000L, 12)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTP0101"));

        // OTP 검증은 계좌개설(되돌릴 수 없는 지점) 앞에 있으므로 아무것도 남지 않아야 한다.
        assertThat(accountJpaRepository.count()).isEqualTo(accountCountBefore);
        assertThat(subscriptionJpaRepository.count()).isEqualTo(subscriptionCountBefore);
    }

    // REQ-PRDT-017 인수기준: "약관 본문을 조회하지 않은 상태로 가입 실행 API를 직접 호출하면
    // 거부된다". 열람 판정은 서비스 단위 테스트(ProductSubscriptionValidationServiceTest)에도 있지만,
    // 인수기준이 겨냥하는 건 실행 엔드포인트라 실제 Redis 열람이력을 태워 확인한다.
    @Test
    @DisplayName("열람이 필요한 약관을 조회하지 않은 채 가입을 실행하면 400 + PRD0005를 반환한다")
    void execute_termsNotViewed_returnsPrd0005() throws Exception {
        Long productId = seedSavingsProduct("EXE-113", false);
        Long termsId = linkSavingsTerms(productId);
        Long withdrawalAccountId = seedAccount("110000009013", customerId, 10_000_000L);

        long accountCountBefore = accountJpaRepository.count();

        mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJsonWithTerms(productId, withdrawalAccountId, termsId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRD0005"));

        assertThat(accountJpaRepository.count()).isEqualTo(accountCountBefore);
    }

    // REQ-PRDT-005 인수기준의 뒷부분: "동의 이력이 저장된다". 미동의 차단(앞부분)은
    // validate_missingRequiredTermsAgreement가 이미 커버하지만, 저장 자체를 확인하는 단언은
    // 없었다 — 기존 성공 테스트는 agreedTerms가 빈 배열이라 저장할 이력이 애초에 없다.
    @Test
    @DisplayName("약관 본문을 열람한 뒤 가입하면 동의 이력이 동의한 버전과 함께 저장된다")
    void execute_savesTermsAgreement() throws Exception {
        Long productId = seedSavingsProduct("EXE-114", false);
        Long termsId = linkSavingsTerms(productId);
        Long withdrawalAccountId = seedAccount("110000009014", customerId, 10_000_000L);

        // 열람 이력은 화면이 보낸 값이 아니라 약관 본문 조회 시점에 서버가 기록한다(REQ-PRDT-017).
        mockMvc.perform(get("/products/{productId}/terms/{termsId}", productId, termsId)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/product-subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(executeRequestJsonWithTerms(productId, withdrawalAccountId, termsId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long subscriptionId = jackson.readTree(response).get("data").get("subscriptionId").asLong();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT terms_version FROM subscription_terms_agreement"
                        + " WHERE subscription_id = ? AND terms_id = ?",
                String.class, subscriptionId, termsId))
                .isEqualTo("v1.0");
    }

    // 적금 상품에는 TERMS_SAVINGS(필수·열람필수, v1.0)를 연결한다 — 시드 약관이라 버전이 고정이다.
    private Long linkSavingsTerms(Long productId) {
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SAVINGS");
        termsRepository.save(ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(productId, termsId))
                .displayOrder((short) 1)
                .build());
        return termsId;
    }

    private String executeRequestJsonWithTerms(Long productId, Long withdrawalAccountId, Long termsId) {
        return """
                {
                  "productId": %d,
                  "subscriptionAmount": 500000,
                  "termMonths": 12,
                  "withdrawalAccountId": %d,
                  "newAccountPassword": "1234",
                  "newAccountPasswordConfirm": "1234",
                  "accountPasswordAuthToken": "ACC_PWD_test",
                  "otpAuthToken": "OTP_AUTH_test",
                  "agreedTerms": [{"termsId": %d, "version": "v1.0"}]
                }
                """.formatted(productId, withdrawalAccountId, termsId);
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

    private Long seedDepositProduct(String productCode) {
        Long productId = productJpaRepository.save(
                ProductTestFixtures.productWithCode(productCode)).getProductId();
        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        new AccountNumberSequenceTestFixture(jdbcTemplate).resetProductAccountSequence(
                productId, AccountType.TIME_DEPOSIT,
                AccountNumberSequenceTestFixture.TIME_DEPOSIT_PREFIX, 0L);
        return productId;
    }

    private long balanceOf(Long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
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
