package com.shinhan.corebank.subscription.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.product.domain.DepositType;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.SaleStatus;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaEntity;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaRepository;
import com.shinhan.corebank.subscription.adapter.out.persistence.SubscriptionTestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


@AutoConfigureMockMvc
@Transactional
class ProductSubscriptionControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProductJpaRepository productJpaRepository;

    @Autowired
    ProductSubscriptionJpaRepository subscriptionJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
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
    @DisplayName("타인 소유 가입건이면 403 + CMN0102를 반환한다")
    void getSubscriptionResult_forbiddenForOtherCustomer() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.defaultProduct()).getProductId();
        Long ownerCustomerId = SubscriptionTestFixtures.insertCustomer(jdbcTemplate, "sub_ctl_owner");
        Long withdrawalAccountId = SubscriptionTestFixtures.insertAccount(jdbcTemplate, "110000000040", ownerCustomerId, null);
        Long subscriptionId = subscriptionJpaRepository.save(
                SubscriptionTestFixtures.defaultSubscription(ownerCustomerId, productId, withdrawalAccountId)
        ).getSubscriptionId();

        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", subscriptionId)
                        .with(authentication(authenticationOf(999_888L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getSubscriptionResult_unauthenticated() throws Exception {
        mockMvc.perform(get("/product-subscriptions/{subscriptionId}", 1L))
                .andExpect(status().isUnauthorized());
    }
}
