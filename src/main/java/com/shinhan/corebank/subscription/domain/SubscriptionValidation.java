package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.product.domain.ProductGroup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionValidation {
    private boolean valid;
    private List<SubscriptionViolation> violations;
    private ProductGroup productGroup;
    private BigDecimal baseRate;
    private BigDecimal preferentialRate;
    private BigDecimal appliedRate;
    private LocalDate maturityDate;
    private Long expectedPrincipal;
    private Long expectedInterest;
    private Long expectedMaturityAmount;
    private String withdrawalAccountNumber;
    private Long withdrawalAccountBalance;
}
