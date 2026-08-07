package com.shinhan.corebank.product.domain;

import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ProductTerms {
    private final ProductTermsId id;
    private final Short displayOrder;

    @Builder
    public ProductTerms(ProductTermsId id, Short displayOrder) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.displayOrder = Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
