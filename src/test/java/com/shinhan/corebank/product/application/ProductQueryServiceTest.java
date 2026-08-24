package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductDetailView;
import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;
import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;
import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsDetail;
import com.shinhan.corebank.product.domain.ProductTermsId;
import com.shinhan.corebank.terms.api.TermsQueryPort;
import com.shinhan.corebank.terms.api.TermsSummary;
import com.shinhan.corebank.product.domain.ProductGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock
    ProductQueryPort productQueryPort;

    @Mock
    TermsQueryPort termsQueryPort;

    @InjectMocks
    ProductQueryService productQueryService;

    @Test
    @DisplayName("허용되지 않은 size면 CMN0005를 던지고 포트는 호출하지 않는다")
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> productQueryService.search(null, null, ProductSortType.RATE, 0, 7, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));

        verify(productQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("page가 음수면 CMN0001을 던지고 포트는 호출하지 않는다")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> productQueryService.search(null, null, ProductSortType.RATE, -1, 10, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verify(productQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("all=true면 size가 허용 목록에 없어도 통과하고 Pageable.unpaged()로 조회한다")
    void search_allTrue_skipsPageSizeValidation_usesUnpaged() {
        Page<Product> expected = new PageImpl<>(List.of());
        when(productQueryPort.search(ProductGroup.DEPOSIT, "적금", ProductSortType.NAME, Pageable.unpaged()))
                .thenReturn(expected);

        Page<Product> result = productQueryService.search(ProductGroup.DEPOSIT, "적금", ProductSortType.NAME, 0, 7, true);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("허용된 size면 포트 결과를 그대로 반환한다")
    void delegatesToPort() {
        Page<Product> expected = new PageImpl<>(List.of());
        when(productQueryPort.search(ProductGroup.DEPOSIT, "적금", ProductSortType.NAME, PageRequest.of(1, 10)))
                .thenReturn(expected);

        Page<Product> result = productQueryService.search(ProductGroup.DEPOSIT, "적금", ProductSortType.NAME, 1, 10, false);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("getDetail은 상품 포트 결과를 그대로 반환하고 약관은 조회하지 않는다")
    void getDetail_delegatesToPort() {
        ProductDetail expected = ProductDetail.builder()
                .rateTiers(List.of())
                .preferentialRates(List.of())
                .terms(List.of())
                .build();
        when(productQueryPort.findDetailByProductId(1L)).thenReturn(Optional.of(expected));

        ProductDetail result = productQueryService.getDetail(1L);

        assertThat(result).isSameAs(expected);
        verify(termsQueryPort, never()).findByIds(any());
    }

    @Test
    @DisplayName("getDetailWithTerms는 상품 포트 결과를 그대로 옮겨 담는다")
    void getDetailWithTerms_copiesProductFields() {
        Product product = Product.builder().productId(1L).productName("청년 희망 적금").build();
        List<ProductRateTier> rateTiers = List.of(ProductRateTier.builder()
                .id(new ProductRateTierId(1L, (short) 12))
                .rate(new BigDecimal("3.20"))
                .build());
        List<ProductPreferentialRate> preferentialRates = List.of(ProductPreferentialRate.builder()
                .productPreferentialRateId(new ProductPreferentialRateId(1L, "AUTO_TRANSFER"))
                .conditionName("자동이체 6회 이상")
                .rate(new BigDecimal("0.50"))
                .build());
        ProductDetail detail = ProductDetail.builder()
                .product(product)
                .rateTiers(rateTiers)
                .preferentialRates(preferentialRates)
                .terms(List.of())
                .build();
        when(productQueryPort.findDetailByProductId(1L)).thenReturn(Optional.of(detail));
        when(termsQueryPort.findByIds(List.of())).thenReturn(List.of());

        ProductDetailView result = productQueryService.getDetailWithTerms(1L);

        assertThat(result.getProduct()).isSameAs(product);
        assertThat(result.getRateTiers()).isSameAs(rateTiers);
        assertThat(result.getPreferentialRates()).isSameAs(preferentialRates);
        assertThat(result.getTerms()).isEmpty();
    }

    @Test
    @DisplayName("약관 연결에 terms 정보를 합치고 포트가 준 표시순서를 유지한다")
    void getDetailWithTerms_mergesTerms() {
        // ProductQueryPort는 display_order 오름차순으로 넘겨준다 (ProductTermsJpaRepositoryTest에서 보장)
        ProductDetail detail = ProductDetail.builder()
                .rateTiers(List.of())
                .preferentialRates(List.of())
                .terms(List.of(productTerms(10L, (short) 1), productTerms(20L, (short) 2)))
                .build();
        when(productQueryPort.findDetailByProductId(1L)).thenReturn(Optional.of(detail));
        // 약관 조회 결과 순서는 보장되지 않으므로 일부러 뒤집어 준다
        when(termsQueryPort.findByIds(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(10L, 20L)))))
                .thenReturn(List.of(
                        new TermsSummary(20L, "마케팅 정보 수신 동의", "v1.1", false, false),
                        new TermsSummary(10L, "예금거래 기본약관", "v1.0", true, true)));

        List<ProductTermsDetail> terms = productQueryService.getDetailWithTerms(1L).getTerms();

        assertThat(terms).extracting(ProductTermsDetail::getTermsId).containsExactly(10L, 20L);
        assertThat(terms.get(0).getTermsName()).isEqualTo("예금거래 기본약관");
        assertThat(terms.get(0).getVersion()).isEqualTo("v1.0");
        assertThat(terms.get(0).isRequired()).isTrue();
        assertThat(terms.get(0).isViewRequired()).isTrue();
        assertThat(terms.get(0).getDisplayOrder()).isEqualTo((short) 1);
        assertThat(terms.get(1).getTermsName()).isEqualTo("마케팅 정보 수신 동의");
        assertThat(terms.get(1).isRequired()).isFalse();
        assertThat(terms.get(1).getDisplayOrder()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("연결된 약관이 terms에 없으면 PRD9001을 던진다")
    void getDetailWithTerms_missingTerms_throwsPrd9001() {
        ProductDetail detail = ProductDetail.builder()
                .rateTiers(List.of())
                .preferentialRates(List.of())
                .terms(List.of(productTerms(10L, (short) 1)))
                .build();
        when(productQueryPort.findDetailByProductId(1L)).thenReturn(Optional.of(detail));
        when(termsQueryPort.findByIds(List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> productQueryService.getDetailWithTerms(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ProductErrorCode.PRODUCT_TERMS_NOT_RESOLVED));
    }

    private ProductTerms productTerms(Long termsId, short displayOrder) {
        return ProductTerms.builder()
                .id(new ProductTermsId(1L, termsId))
                .displayOrder(displayOrder)
                .build();
    }

    @Test
    @DisplayName("존재하지 않는 productId면 PRD0201을 던진다")
    void getDetail_notFound_throwsPrd0201() {
        when(productQueryPort.findDetailByProductId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productQueryService.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
