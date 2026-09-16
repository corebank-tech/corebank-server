package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ProductSubscription {
    private Long subscriptionId;
    private Long customerId;
    private Long productId;
    private Long accountId;
    private Long withdrawalAccountId;
    private Long subscriptionAmount;
    private Short termMonths;
    private Byte paymentDay;
    private BigDecimal baseRate;
    private BigDecimal preferentialRate;
    private BigDecimal appliedRate;
    private MaturityHandling maturityHandling;
    private Long expectedMaturityAmount;
    private ProcessResultStatus status;
    private String transactionNumber;
    private LocalDate openedDate;
    private LocalDate maturityDate;
    private LocalDateTime subscribedAt;
}
