package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsSummary;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductTerms;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService implements ProductQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(5, 10, 20, 30, 50);

    private final ProductQueryPort productQueryPort;
    private final TermsQueryPort termsQueryPort;

    @Override
    public Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, int page, int size) {
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다.");
        }
        // TODO: 페이지 크기 검증 추출해야함

        return productQueryPort.search(productGroup, keyword, sort, PageRequest.of(page, size));
    }

    @Override
    public ProductDetail getDetail(Long productId) {
        ProductDetail detail = productQueryPort.findDetailByProductId(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return ProductDetail.builder()
                .product(detail.getProduct())
                .rateTiers(detail.getRateTiers())
                .preferentialRates(detail.getPreferentialRates())
                .terms(fillTermsInfo(detail.getTerms()))
                .build();
    }

    /**
     * product_terms는 연결 정보(termsId, displayOrder)만 가지고 약관 이름·버전·동의 여부는
     * terms 테이블이 가진다. 상품 영속성 어댑터가 다른 컨텍스트의 테이블을 조인하지 않도록
     * 두 출처를 이 계층에서 합친다.
     */
    private List<ProductTerms> fillTermsInfo(List<ProductTerms> terms) {
        if (terms.isEmpty()) {
            return terms;
        }

        Map<Long, TermsSummary> summaries = termsQueryPort
                .findByIds(terms.stream().map(t -> t.getId().getTermsId()).toList())
                .stream()
                .collect(Collectors.toMap(TermsSummary::termsId, Function.identity()));

        return terms.stream()
                .map(t -> {
                    TermsSummary summary = summaries.get(t.getId().getTermsId());
                    return summary == null ? t : t.withTermsInfo(
                            summary.title(), summary.version(), summary.isRequired(), summary.viewRequired());
                })
                .toList();
    }
}
