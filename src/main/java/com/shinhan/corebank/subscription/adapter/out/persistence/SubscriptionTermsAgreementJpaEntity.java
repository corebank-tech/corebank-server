package com.shinhan.corebank.subscription.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SubscriptionTermsAgreementJpaEntity {
    @EmbeddedId
    private SubscriptionTermsAgreementJpaEntityId id;

    @Column(nullable = false, length = 10)
    private String termsVersion;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime agreedAt;
}
