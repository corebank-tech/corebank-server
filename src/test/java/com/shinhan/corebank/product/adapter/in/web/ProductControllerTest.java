package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

    @BeforeEach
    void seedProduct() {
        productJpaRepository.save(ProductJpaEntity.builder()
                .productCode("CTL-101")
                .productName("정기예금 컨트롤러 테스트")
                .productGroup(ProductGroup.DEPOSIT)
                .depositType(DepositType.LUMP_SUM)
                .baseRate(new BigDecimal("3.00"))
                .maxRate(new BigDecimal("3.50"))
                .minAmount(100_000L)
                .maxAmount(100_000_000L)
                .amountUnit(10_000L)
                .minTermMonths((short) 6)
                .maxTermMonths((short) 36)
                .interestPayType(InterestPayType.SIMPLE)
                .saleStatus(SaleStatus.ON_SALE)
                .newFlag(false)
                .singleAccountLimit(false)
                .build());
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
}
