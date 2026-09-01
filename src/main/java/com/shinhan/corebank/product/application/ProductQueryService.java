package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.util.PageableResolver;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductDetailView;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsDetail;
import com.shinhan.corebank.terms.api.TermsQueryPort;
import com.shinhan.corebank.terms.api.TermsSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService implements ProductQueryUseCase {
    // 상수명 통일(ALLOWED_PAGE_SIZES -> ALLOWED_PAGE_SIZE) - 다른 도메인 서비스들과 동일하게 맞춤(#297 리뷰)
    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);

    private final ProductQueryPort productQueryPort;
    private final TermsQueryPort termsQueryPort;

    @Override
    public Page<Product> search(
            ProductGroup productGroup, String keyword, ProductSortType sort, int page, int size, boolean all) {
        Pageable pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZE);

        return productQueryPort.search(productGroup, keyword, sort, pageable);
    }

    @Override
    public ProductDetail getDetail(Long productId) {
        return productQueryPort
                .findDetailByProductId(productId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    @Override
    public ProductDetailView getDetailWithTerms(Long productId) {
        ProductDetail detail = getDetail(productId);
        return new ProductDetailView(detail, termsOf(detail.getTerms()));
    }

    // product_terms의 약관 연결에 terms 모듈의 약관 정보를 합친다.
    // 순서는 ProductQueryPort가 표시순서대로 넘겨준 것을 그대로 유지한다.
    private List<ProductTermsDetail> termsOf(List<ProductTerms> productTerms) {
        List<Long> termsIds =
                productTerms.stream().map(terms -> terms.getId().getTermsId()).toList();
        Map<Long, TermsSummary> summaries = termsQueryPort.findByIds(termsIds).stream()
                .collect(Collectors.toMap(TermsSummary::termsId, Function.identity()));

        return productTerms.stream()
                .map(terms -> toDetail(terms, summaries.get(terms.getId().getTermsId())))
                .toList();
    }

    private ProductTermsDetail toDetail(ProductTerms productTerms, TermsSummary summary) {
        // product_terms.terms_id에 FK가 걸려 있어 정상적으로는 발생하지 않는다.
        // 클라이언트 잘못이 아니라 데이터 정합성이 깨진 것이므로 도메인 내부 오류로 드러낸다.
        if (summary == null) {
            // BusinessException의 커스텀 메시지는 응답 본문에 그대로 실리므로 식별자는 로그로만 남긴다.
            log.error(
                    "product_terms에 연결된 약관이 terms에 없습니다 - productId={}, termsId={}",
                    productTerms.getId().getProductId(),
                    productTerms.getId().getTermsId());
            throw new BusinessException(ProductErrorCode.PRODUCT_TERMS_NOT_RESOLVED);
        }
        return ProductTermsDetail.builder()
                .termsId(summary.termsId())
                .termsName(summary.title())
                .version(summary.version())
                .required(summary.isRequired())
                .viewRequired(summary.viewRequired())
                .displayOrder(productTerms.getDisplayOrder())
                .build();
    }
}
