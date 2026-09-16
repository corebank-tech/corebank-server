package com.shinhan.corebank.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubscriptionTermsAgreementIdTest {

    @ParameterizedTest(name = "subscriptionId={0}, termsId={1}")
    @CsvSource(
            nullValues = "null",
            value = {"null, 2", "1,    null", "null, null"})
    @DisplayName("subscriptionId 또는 termsId가 null이면 CMN0002를 던진다")
    void rejectsNull(Long subscriptionId, Long termsId) {
        assertThatThrownBy(() -> new SubscriptionTermsAgreementId(subscriptionId, termsId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("두 값이 모두 있으면 정상 생성된다")
    void createsWithBothValues() {
        SubscriptionTermsAgreementId id = new SubscriptionTermsAgreementId(1L, 2L);

        assertThat(id.getSubscriptionId()).isEqualTo(1L);
        assertThat(id.getTermsId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("값이 같으면 동등하고 해시도 같다")
    void equalsAndHashCode() {
        var id = new SubscriptionTermsAgreementId(1L, 2L);

        assertThat(id)
                .isEqualTo(new SubscriptionTermsAgreementId(1L, 2L))
                .hasSameHashCodeAs(new SubscriptionTermsAgreementId(1L, 2L));
        assertThat(id).isNotEqualTo(new SubscriptionTermsAgreementId(1L, 3L));
    }
}
