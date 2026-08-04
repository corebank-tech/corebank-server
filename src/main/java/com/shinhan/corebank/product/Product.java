package com.shinhan.corebank.product;

import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(length = 20, nullable = false,  unique = true)
    private String productCode;

    @Column(length = 100, nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private ProductGroup productGroup;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private DepositType depositType;

    @Column(length = 200)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal baseRate;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal maxRate;

    @Column(nullable = false)
    private Long minAmount;

    @Column(nullable = false)
    private Long maxAmount;

    @Column(nullable = false)
    private Long amountUnit;

    @Column(nullable = false)
    private Short min_term_months;

    @Column(nullable = false)
    private Short max_term_months;

    @Column(length = 20, nullable = false)
    private String interest_pay_type;

    @Enumerated(EnumType.STRING)
    @Column(length = 12, nullable = false)
    private SaleStatus sale_status;

    private LocalDate saleStartDate;
    private LocalDate saleEndDate;

    @Column(nullable = false)
    private Boolean newFlag;

    @Column(nullable = false)
    private Boolean single_account_limit;
}