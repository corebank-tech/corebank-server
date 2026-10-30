package com.shinhan.corebank.product.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsViewHistoryJpaEntityId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "terms_id")
    private Long termsId;
}
