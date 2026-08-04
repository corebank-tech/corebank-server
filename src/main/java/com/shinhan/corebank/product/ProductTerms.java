package com.shinhan.corebank.product;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTerms {

    @EmbeddedId
    private ProductTermsId id;
}
