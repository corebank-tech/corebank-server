package com.shinhan.corebank.product.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class Product {
    private Long productId;
    private String productCode;
    private String productName;
    private ProductGroup productGroup;
    private DepositType depositType;
    private String summary;
    private String description;
    private String eligibility;
    private List<String> subscriptionRestrictions;
    private List<String> notices;
    private BigDecimal baseRate;
    private BigDecimal maxRate;
    private Long minAmount;
    private Long maxAmount;
    private Long amountUnit;
    private Short minTermMonths;
    private Short maxTermMonths;
    private InterestPayType interestPayType;
    private SaleStatus saleStatus;
    private LocalDate saleStartDate;
    private LocalDate saleEndDate;
    private Boolean newFlag;
    private Boolean singleAccountLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
