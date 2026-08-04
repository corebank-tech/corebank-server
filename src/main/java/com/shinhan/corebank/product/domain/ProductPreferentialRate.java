package com.shinhan.corebank.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_preferential_rate")
@Getter
@NoArgsConstructor
public class ProductPreferentialRate {
    @EmbeddedId
    private ProductPreferentialRateId productPreferentialRateId;

    @Column(length = 100, nullable = false)
    private String conditionName;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal rate;
}
