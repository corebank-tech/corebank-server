package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * {@code @Embeddable} 이라 Hibernate 가 기본 생성자로 인스턴스를 만든 뒤 필드를 채운다.
 * 기본 생성자를 제거하면 안 되고, 같은 이유로 필드를 final 로 둘 수 없다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class SubscriptionTermsAgreementJpaEntityId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long subscriptionId;
    private Long termsId;

    public SubscriptionTermsAgreementJpaEntityId(Long subscriptionId, Long termsId) {
        if (subscriptionId == null || termsId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.subscriptionId = subscriptionId;
        this.termsId = termsId;
    }
}
