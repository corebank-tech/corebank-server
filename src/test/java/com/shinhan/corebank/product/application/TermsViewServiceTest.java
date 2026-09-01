package com.shinhan.corebank.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsView;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import com.shinhan.corebank.product.domain.ProductTermsView;
import com.shinhan.corebank.terms.api.TermsDetail;
import com.shinhan.corebank.terms.api.TermsQueryPort;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermsViewServiceTest {

    @Mock
    ProductQueryPort productQueryPort;

    @Mock
    TermsQueryPort termsQueryPort;

    @Mock
    TermsViewHistoryPort termsViewHistoryPort;

    @InjectMocks
    TermsViewService termsViewService;

    @Test
    @DisplayName("상품이 없으면 PRD0201을 던진다")
    void view_productNotFound() {
        when(productQueryPort.existsProduct(1L)).thenReturn(false);

        assertThatThrownBy(() -> termsViewService.view(1L, 301L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("상품은 있지만 product_terms 연결이 없으면 PRD0202를 던진다")
    void view_termsNotLinked() {
        when(productQueryPort.existsProduct(1L)).thenReturn(true);
        when(productQueryPort.existsProductTerms(1L, 301L)).thenReturn(false);

        assertThatThrownBy(() -> termsViewService.view(1L, 301L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ProductErrorCode.TERMS_NOT_FOUND));
    }

    @Test
    @DisplayName("연결은 있지만 TermsQueryPort가 빈 Optional이면 PRD0202를 던진다")
    void view_termsDetailMissing() {
        when(productQueryPort.existsProduct(1L)).thenReturn(true);
        when(productQueryPort.existsProductTerms(1L, 301L)).thenReturn(true);
        when(termsQueryPort.findDetailById(301L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termsViewService.view(1L, 301L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ProductErrorCode.TERMS_NOT_FOUND));
    }

    @Test
    @DisplayName("정상 케이스면 열람 이력을 기록하고 필드를 매핑한 결과를 반환한다")
    void view_success() {
        when(productQueryPort.existsProduct(1L)).thenReturn(true);
        when(productQueryPort.existsProductTerms(1L, 301L)).thenReturn(true);
        when(termsQueryPort.findDetailById(301L))
                .thenReturn(Optional.of(new TermsDetail(301L, "예금거래기본약관", "v1.2", true, true, "제1조...")));
        LocalDateTime viewedAt = LocalDateTime.of(2026, 8, 12, 10, 0);
        when(termsViewHistoryPort.record(10L, 301L)).thenReturn(new TermsView(viewedAt, viewedAt.plusMinutes(30)));

        ProductTermsView result = termsViewService.view(1L, 301L, 10L);

        assertThat(result.getTermsId()).isEqualTo(301L);
        assertThat(result.getTermsName()).isEqualTo("예금거래기본약관");
        assertThat(result.getContent()).isEqualTo("제1조...");
        assertThat(result.getViewedAt()).isEqualTo(viewedAt);
        assertThat(result.getViewExpiresAt()).isEqualTo(viewedAt.plusMinutes(30));
    }

    @Test
    @DisplayName("isViewed는 TermsViewHistoryPort 조회 결과 존재 여부를 그대로 반환한다")
    void isViewed_delegates() {
        when(termsViewHistoryPort.find(10L, 301L))
                .thenReturn(Optional.of(new TermsView(LocalDateTime.now(), LocalDateTime.now())));

        assertThat(termsViewService.isViewed(10L, 301L)).isTrue();
    }
}
