package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProductSubscriptionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long subscriptionId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long productId;

    @Column
    private Long accountId;

    @Column(nullable = false)
    private Long withdrawalAccountId;

    @Column(nullable = false)
    private Long subscriptionAmount;

    @Column(nullable = false)
    private Short termMonths;

    @Column
    private Byte paymentDay;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal baseRate;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal preferentialRate;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal appliedRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MaturityHandling maturityHandling;

    @Column
    private Long expectedMaturityAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ProcessResultStatus status;

    @Column(columnDefinition = "CHAR(20)")
    private String transactionNumber;

    private LocalDate openedDate;
    private LocalDate maturityDate;

    @Column(nullable = false)
    private LocalDateTime subscribedAt;
}
