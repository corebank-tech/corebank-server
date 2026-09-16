package com.shinhan.corebank.subscription.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
