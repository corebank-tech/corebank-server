package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaEntity;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.product.domain.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
    @DisplayName("상품 상세를 200 + ApiResponse 봉투로 반환한다")
    void getProductDetail() throws Exception {
        ProductJpaEntity saved = productJpaRepository.save(ProductTestFixtures.productWithCode("DTL-101"));

        mockMvc.perform(get("/products/{productId}", saved.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.productId").value(saved.getProductId()))
                .andExpect(jsonPath("$.data.productCode").value("DTL-101"))
                .andExpect(jsonPath("$.data.rateTiers").isArray())
                .andExpect(jsonPath("$.data.preferentialRates").isArray())
                .andExpect(jsonPath("$.data.terms").isArray());
    }

    @Test
    @DisplayName("존재하지 않는 productId면 404 + PRD0201을 반환한다")
    void getProductDetail_notFound() throws Exception {
        mockMvc.perform(get("/products/{productId}", 999_999L))
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
}
