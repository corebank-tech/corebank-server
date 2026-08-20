package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 상품에 연결된 약관 한 건 (product_terms의 표시순서 + terms 모듈의 약관 정보)
@Getter
@AllArgsConstructor
@Builder
public class ProductTermsDetail {
    private final Long termsId;
    private final String termsName;
    private final String version;
    private final boolean required;
    private final boolean viewRequired;
    private final short displayOrder;
}
