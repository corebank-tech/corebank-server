package com.shinhan.corebank.subscription.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreementId;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionTermsAgreementMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 복합키 포함 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        SubscriptionTermsAgreementJpaEntity entity = SubscriptionTermsAgreementJpaEntity.builder()
                .id(new SubscriptionTermsAgreementJpaEntityId(1L, 2L))
                .termsVersion("v1.0")
                .readAt(LocalDateTime.of(2026, 8, 1, 10, 29, 0))
                .agreedAt(LocalDateTime.of(2026, 8, 1, 10, 30, 0))
                .build();

        SubscriptionTermsAgreement domain = SubscriptionTermsAgreementMapper.toDomain(entity);

        assertThat(domain.getId().getSubscriptionId()).isEqualTo(1L);
        assertThat(domain.getId().getTermsId()).isEqualTo(2L);
        assertThat(domain.getTermsVersion()).isEqualTo(entity.getTermsVersion());
        assertThat(domain.getReadAt()).isEqualTo(entity.getReadAt());
        assertThat(domain.getAgreedAt()).isEqualTo(entity.getAgreedAt());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 복합키 포함 모든 필드가 보존된다")
    void toEntity_preservesAllFields() {
        SubscriptionTermsAgreement domain = SubscriptionTermsAgreement.builder()
                .id(new SubscriptionTermsAgreementId(1L, 2L))
                .termsVersion("v1.0")
                .readAt(LocalDateTime.of(2026, 8, 1, 10, 29, 0))
                .agreedAt(LocalDateTime.of(2026, 8, 1, 10, 30, 0))
                .build();

        SubscriptionTermsAgreementJpaEntity entity = SubscriptionTermsAgreementMapper.toEntity(domain);

        assertThat(entity.getId().getSubscriptionId()).isEqualTo(1L);
        assertThat(entity.getId().getTermsId()).isEqualTo(2L);
        assertThat(entity.getTermsVersion()).isEqualTo(domain.getTermsVersion());
        assertThat(entity.getReadAt()).isEqualTo(domain.getReadAt());
        assertThat(entity.getAgreedAt()).isEqualTo(domain.getAgreedAt());
    }
}
