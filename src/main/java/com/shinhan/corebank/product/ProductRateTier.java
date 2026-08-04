package com.shinhan.corebank.product;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "product_rate_tier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRateTier {
    @EmbeddedId
    private ProductRateTierId id;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal rate;

}
