package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class ProductTermsId {
    private Long productId;
    private Long termsId;
}
