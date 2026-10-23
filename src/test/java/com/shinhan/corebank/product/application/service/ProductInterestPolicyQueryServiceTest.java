package com.shinhan.corebank.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.api.InterestCalculationMode;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductInterestPolicyQueryServiceTest {

    private static final Long PRODUCT_ID = 10L;

    @Mock
    private ProductQueryUseCase productQueryUseCase;

    @InjectMocks
    private ProductInterestPolicyQueryService service;

    @Test
    void getMode_simple() {
        stubProduct(InterestPayType.SIMPLE);

        assertThat(service.getMode(PRODUCT_ID)).isEqualTo(InterestCalculationMode.SIMPLE);
    }

    @Test
    void getMode_compound() {
        stubProduct(InterestPayType.COMPOUND);

        assertThat(service.getMode(PRODUCT_ID)).isEqualTo(InterestCalculationMode.MONTHLY_COMPOUND);
    }

    @Test
    void getMode_nullDetail() {
        when(productQueryUseCase.getDetail(PRODUCT_ID)).thenReturn(null);

        assertInternalError();
    }

    @Test
    void getMode_nullProduct() {
        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(ProductDetail.builder().build());

        assertInternalError();
    }

    @Test
    void getMode_nullInterestPayType() {
        stubProduct(null);

        assertInternalError();
    }

    private void stubProduct(InterestPayType payType) {
        Product product = Product.builder().interestPayType(payType).build();

        when(productQueryUseCase.getDetail(PRODUCT_ID))
                .thenReturn(ProductDetail.builder().product(product).build());
    }

    private void assertInternalError() {
        assertThatThrownBy(() -> service.getMode(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
    }
}
