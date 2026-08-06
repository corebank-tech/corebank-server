package com.shinhan.corebank.product.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_preferential_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProductPreferentialRateJpaEntity {
    @EmbeddedId
    private ProductPreferentialRateJpaEntityId productPreferentialRateId;

    @Column(length = 100, nullable = false)
    private String conditionName;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal rate;
}
