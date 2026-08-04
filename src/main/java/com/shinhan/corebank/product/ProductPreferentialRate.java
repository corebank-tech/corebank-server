package com.shinhan.corebank.product;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
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
