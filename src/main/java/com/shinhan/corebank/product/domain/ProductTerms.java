package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ProductTerms {
    private final ProductTermsId id;
    private final Short displayOrder;

    // 약관 이름·버전·동의 여부는 product_terms가 아니라 terms 테이블이 가진다.
    // 상품 영속성 어댑터는 다른 컨텍스트인 terms를 조인하지 않으므로 여기서는 비어 있고,
    // ProductQueryService가 TermsQueryPort로 조회해 withTermsInfo()로 채운다.
    private final String termsName;
    private final String version;
    private final Boolean required;
    private final Boolean viewRequired;

    @Builder
    public ProductTerms(ProductTermsId id, Short displayOrder,
                        String termsName, String version, Boolean required, Boolean viewRequired) {
        if (id == null || displayOrder == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.id = id;
        this.displayOrder = displayOrder;
        this.termsName = termsName;
        this.version = version;
        this.required = required;
        this.viewRequired = viewRequired;
    }

    public ProductTerms withTermsInfo(String termsName, String version, Boolean required, Boolean viewRequired) {
        return ProductTerms.builder()
                .id(id)
                .displayOrder(displayOrder)
                .termsName(termsName)
                .version(version)
                .required(required)
                .viewRequired(viewRequired)
                .build();
    }
}
