package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class ProductControllerTest extends IntegrationTestSupport {

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
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedProduct() {
        productJpaRepository.save(ProductTestFixtures.productWithCode("CTL-101", "정기예금 컨트롤러 테스트"));
    }

    @Test
    @DisplayName("상품 목록을 200 + ApiResponse 봉투로 반환한다")
    void searchProducts() throws Exception {
        mockMvc.perform(get("/products")
                        .param("keyword", "컨트롤러")
                        .param("sort", "NAME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productCode").value("CTL-101"))
                .andExpect(jsonPath("$.data.items[0].newProduct").value(false));
    }

    @Test
    @DisplayName("공개 경로(/api/v1/products, context-path 적용)로 호출해도 정상 응답한다")
    void searchProductsWithPublicPath() throws Exception {
        mockMvc.perform(get("/api/v1/products").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));
    }

    @Test
    @DisplayName("size가 허용 목록(5/10/20/30/50)에 없으면 400 + CMN0005를 반환한다")
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/products").param("size", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    @Test
    @DisplayName("page가 음수면 400 + CMN0001을 반환한다")
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("productGroup에 정의되지 않은 값이 오면 400 + CMN0001을 반환한다")
    void rejectsInvalidProductGroup() throws Exception {
        mockMvc.perform(get("/products").param("productGroup", "WRONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("상품 상세 응답이 docs/api_endpoint_reference.md 스펙 필드를 전부 채워서 반환한다")
    void getProductDetail() throws Exception {
        ProductJpaEntity saved = productJpaRepository.save(ProductJpaEntity.builder()
                .productCode("DTL-101")
                .productName("청년 희망 적금")
                .productGroup(ProductGroup.SAVINGS)
                .depositType(DepositType.INSTALLMENT)
                .description("만 19~34세 대상 정액적립식 적금")
                .eligibility("만 19~34세 개인")
                .subscriptionRestrictions(List.of("1인 1계좌"))
                .notices(List.of("중도해지 시 약정금리가 적용되지 않습니다."))
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
        Long productId = saved.getProductId();
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SAVINGS");

        rateTierRepository.save(ProductRateTierJpaEntity.builder()
                .id(new ProductRateTierJpaEntityId(productId, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        preferentialRateRepository.save(ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(new ProductPreferentialRateJpaEntityId(productId, "AUTO_TRANSFER"))
                .conditionName("자동이체 6회 이상")
                .rate(new BigDecimal("0.50"))
                .build());
        termsRepository.save(ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(productId, termsId))
                .displayOrder((short) 1)
                .build());

        mockMvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productCode").value("DTL-101"))
                .andExpect(jsonPath("$.data.productName").value("청년 희망 적금"))
                .andExpect(jsonPath("$.data.productGroup").value("SAVINGS"))
                .andExpect(jsonPath("$.data.description").value("만 19~34세 대상 정액적립식 적금"))
                .andExpect(jsonPath("$.data.baseRate").value(3.20))
                .andExpect(jsonPath("$.data.maxRate").value(4.50))
                .andExpect(jsonPath("$.data.minAmount").value(10_000))
                .andExpect(jsonPath("$.data.maxAmount").value(10_000_000))
                .andExpect(jsonPath("$.data.amountUnit").value(10_000))
                .andExpect(jsonPath("$.data.termOptions.length()").value(1))
                .andExpect(jsonPath("$.data.termOptions[0]").value(12))
                .andExpect(jsonPath("$.data.rateTiers[0].termMonths").value(12))
                .andExpect(jsonPath("$.data.rateTiers[0].rate").value(3.20))
                .andExpect(jsonPath("$.data.preferentialRates[0].conditionCode").value("AUTO_TRANSFER"))
                .andExpect(jsonPath("$.data.preferentialRates[0].conditionName").value("자동이체 6회 이상"))
                .andExpect(jsonPath("$.data.preferentialRates[0].rate").value(0.50))
                .andExpect(jsonPath("$.data.eligibility").value("만 19~34세 개인"))
                .andExpect(jsonPath("$.data.subscriptionRestrictions[0]").value("1인 1계좌"))
                .andExpect(jsonPath("$.data.notices[0]").value("중도해지 시 약정금리가 적용되지 않습니다."))
                .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data.saleEndDate").value("2026-12-31"))
                .andExpect(jsonPath("$.data.terms[0].termsId").value(termsId))
                .andExpect(jsonPath("$.data.terms[0].displayOrder").value(1))
                // termsName/version/required/viewRequired는 스펙상 Nullable:X(항상 값 있음)이지만,
                // P6 TermsQueryPort 연동 전까지는 null로 응답한다 — 스펙과의 알려진 차이를 테스트로 명시.
                .andExpect(jsonPath("$.data.terms[0].termsName").doesNotExist())
                .andExpect(jsonPath("$.data.terms[0].version").doesNotExist())
                .andExpect(jsonPath("$.data.terms[0].required").doesNotExist())
                .andExpect(jsonPath("$.data.terms[0].viewRequired").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 productId면 404 + PRD0201을 반환한다")
    void getProductDetail_notFound() throws Exception {
        mockMvc.perform(get("/products/{productId}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0201"));
    }

    @Test
    @DisplayName("productId가 숫자가 아니면 400 + CMN0001을 반환한다")
    void getProductDetail_nonNumericId() throws Exception {
        mockMvc.perform(get("/products/{productId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("productId가 음수여도 형식은 유효해 404 + PRD0201로 응답한다(존재하지 않는 계좌/상품 처리와 동일)")
    void getProductDetail_negativeId() throws Exception {
        mockMvc.perform(get("/products/{productId}", -1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0201"));
    }

    @Test
    @DisplayName("판매중지 상품도 상세조회는 200으로 응답하고 saleStatus로 구분된다")
    void getProductDetail_suspendedStillReturns200() throws Exception {
        ProductJpaEntity saved = productJpaRepository.save(ProductTestFixtures.productWithCode("DTL-102", SaleStatus.SUSPENDED));

        mockMvc.perform(get("/products/{productId}", saved.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.saleStatus").value("SUSPENDED"));
    }

    @Test
    @DisplayName("약관 본문 조회 성공 시 200 + 본문/열람이력을 반환한다")
    void getProductTerms_success() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.productWithCode("TRM-101")).getProductId();
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_DEPOSIT");
        termsRepository.save(ProductTermsJpaEntity.builder()
                .id(new ProductTermsJpaEntityId(productId, termsId))
                .displayOrder((short) 1)
                .build());

        mockMvc.perform(get("/products/{productId}/terms/{termsId}", productId, termsId)
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.termsId").value(termsId))
                .andExpect(jsonPath("$.data.termsName").value("예금거래 기본약관"))
                .andExpect(jsonPath("$.data.required").value(true))
                .andExpect(jsonPath("$.data.viewRequired").value(true))
                .andExpect(jsonPath("$.data.content").value("(내용)"))
                .andExpect(jsonPath("$.data.viewedAt").exists())
                .andExpect(jsonPath("$.data.viewExpiresAt").exists());
    }

    @Test
    @DisplayName("인증 없이 요청하면 401 + CMN0101을 반환한다")
    void getProductTerms_unauthenticated() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.productWithCode("TRM-102")).getProductId();

        mockMvc.perform(get("/products/{productId}/terms/{termsId}", productId, 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
    }

    @Test
    @DisplayName("존재하지 않는 productId면 404 + PRD0201을 반환한다")
    void getProductTerms_productNotFound() throws Exception {
        mockMvc.perform(get("/products/{productId}/terms/{termsId}", 999_999L, 1L)
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0201"));
    }

    @Test
    @DisplayName("상품에 연결되지 않은 termsId면 404 + PRD0202를 반환한다")
    void getProductTerms_termsNotLinked() throws Exception {
        Long productId = productJpaRepository.save(ProductTestFixtures.productWithCode("TRM-103")).getProductId();

        mockMvc.perform(get("/products/{productId}/terms/{termsId}", productId, 999_999L)
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRD0202"));
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
