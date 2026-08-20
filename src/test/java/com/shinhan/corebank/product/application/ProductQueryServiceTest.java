package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsSummary;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsId;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        assertThatThrownBy(() -> productQueryService.search(null, null, ProductSortType.RATE, 0, 7))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE));

        verify(productQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("page가 음수면 CMN0001을 던지고 포트는 호출하지 않는다")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> productQueryService.search(null, null, ProductSortType.RATE, -1, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verify(productQueryPort, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("허용된 size면 포트 결과를 그대로 반환한다")
    void delegatesToPort() {
        Page<Product> expected = new PageImpl<>(List.of());
        when(productQueryPort.search(ProductGroup.DEPOSIT, "적금", ProductSortType.NAME, PageRequest.of(1, 10)))
                .thenReturn(expected);

        Page<Product> result = productQueryService.search(ProductGroup.DEPOSIT, "적금", ProductSortType.NAME, 1, 10);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("연결된 약관이 없으면 약관 포트를 호출하지 않고 포트 결과를 그대로 반환한다")
    void getDetail_withoutTerms_skipsTermsQuery() {
        Product product = Product.builder().productId(1L).build();
        ProductDetail found = ProductDetail.builder()
                .product(product)
                .rateTiers(List.of())
                .preferentialRates(List.of())
                .terms(List.of())
                .build();
        when(productQueryPort.findDetailByProductId(1L)).thenReturn(Optional.of(found));

        ProductDetail result = productQueryService.getDetail(1L);

        assertThat(result.getProduct()).isSameAs(product);
        assertThat(result.getTerms()).isEmpty();
        verify(termsQueryPort, never()).findByIds(any());
    }

    @Test
    @DisplayName("연결된 약관의 이름·버전·동의 여부를 TermsQueryPort로 채워 반환한다")
    void getDetail_fillsTermsInfo() {
        ProductTerms link = ProductTerms.builder()
                .id(new ProductTermsId(1L, 7L))
                .displayOrder((short) 1)
                .build();
        ProductDetail found = ProductDetail.builder()
                .product(Product.builder().productId(1L).build())
                .rateTiers(List.of())
                .preferentialRates(List.of())
                .terms(List.of(link))
                .build();
        when(productQueryPort.findDetailByProductId(1L)).thenReturn(Optional.of(found));
        when(termsQueryPort.findByIds(List.of(7L)))
                .thenReturn(List.of(new TermsSummary(7L, "적립식예금 약관", "v1.0", true, true)));

        ProductDetail result = productQueryService.getDetail(1L);

        assertThat(result.getTerms()).singleElement().satisfies(terms -> {
            assertThat(terms.getId().getTermsId()).isEqualTo(7L);
            assertThat(terms.getDisplayOrder()).isEqualTo((short) 1);
            assertThat(terms.getTermsName()).isEqualTo("적립식예금 약관");
            assertThat(terms.getVersion()).isEqualTo("v1.0");
            assertThat(terms.getRequired()).isTrue();
            assertThat(terms.getViewRequired()).isTrue();
        });
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
