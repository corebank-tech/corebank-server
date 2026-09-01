package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_number_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountNumberSequenceJpaEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_id")
    private long sequenceId;

    @Column(name = "bank_code", nullable = false, length = 3)
    private String bankCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    private AccountType accountType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_prefix", nullable = false, length = 2)
    private String productPrefix;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    public void updateLastSequence(long lastSequence) {
        this.lastSequence = lastSequence;
    }
}
