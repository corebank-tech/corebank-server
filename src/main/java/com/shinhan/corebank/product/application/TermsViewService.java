package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.application.port.in.TermsViewUseCase;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsDetail;
import com.shinhan.corebank.product.application.port.out.TermsQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsView;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import com.shinhan.corebank.product.domain.ProductTermsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsViewService implements TermsViewUseCase {

    private final ProductQueryPort productQueryPort;
    private final TermsQueryPort termsQueryPort;
    private final TermsViewHistoryPort termsViewHistoryPort;

    @Override
    public ProductTermsView view(Long productId, Long termsId, Long customerId) {
        if (!productQueryPort.existsProduct(productId)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!productQueryPort.existsProductTerms(productId, termsId)) {
            throw new BusinessException(ProductErrorCode.TERMS_NOT_FOUND);
        }
        TermsDetail detail = termsQueryPort.findDetailById(termsId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.TERMS_NOT_FOUND));

        TermsView view = termsViewHistoryPort.record(customerId, termsId);

        return ProductTermsView.builder()
                .termsId(detail.termsId())
                .termsName(detail.title())
                .version(detail.version())
                .required(detail.isRequired())
                .viewRequired(detail.viewRequired())
                .content(detail.content())
                .viewedAt(view.viewedAt())
                .viewExpiresAt(view.viewExpiresAt())
                .build();
    }

    @Override
    public boolean isViewed(Long customerId, Long termsId) {
        return termsViewHistoryPort.find(customerId, termsId).isPresent();
    }
}
