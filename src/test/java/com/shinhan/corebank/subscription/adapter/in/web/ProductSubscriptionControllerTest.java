package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaEntity;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductRateTierJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTermsJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTermsJpaEntityId;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTermsJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
                .andExpect(jsonPath("$.data.violations[0].field").value("agreedTerms"));
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