package com.shinhan.corebank.product.domain;

import lombok.RequiredArgsConstructor;
import lombok.Getter;

import java.util.List;

// 상품 상세조회 결과. 상품 상세에 terms 모듈의 약관 정보를 더한 모델이다.
@RequiredArgsConstructor
public class ProductDetailView {
    // 밖으로 열지 않는다. detail.getTerms()는 약관명·버전이 없는 연결 정보라
    // 노출하면 아래 getTerms()와 헷갈려 응답에서 필드가 빠지는 식으로만 드러난다.
    private final ProductDetail detail;

    @Getter
    private final List<ProductTermsDetail> terms;

    public Product getProduct() {
        return detail.getProduct();
    }

    public List<ProductRateTier> getRateTiers() {
        return detail.getRateTiers();
    }

    public List<ProductPreferentialRate> getPreferentialRates() {
        return detail.getPreferentialRates();
    }
}
