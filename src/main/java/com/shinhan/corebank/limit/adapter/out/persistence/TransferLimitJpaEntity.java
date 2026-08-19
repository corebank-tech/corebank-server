package com.shinhan.corebank.limit.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.limit.domain.TransferLimit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer_limit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferLimitJpaEntity extends BaseEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "one_time_limit", nullable = false)
    private long oneTimeLimit;

    @Column(name = "daily_limit", nullable = false)
    private long dailyLimit;

    /** 낙관적 락. 한도 변경이 동시에 들어오면 나중 저장이 충돌로 감지된다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private TransferLimitJpaEntity(TransferLimit limit) {
        this.customerId = limit.getCustomerId();
        this.oneTimeLimit = limit.getOneTimeLimit();
        this.dailyLimit = limit.getDailyLimit();
        this.version = limit.getVersion();
    }

    static TransferLimitJpaEntity from(TransferLimit limit) {
        return new TransferLimitJpaEntity(limit);
    }

    TransferLimit toDomain() {
        return TransferLimit.restore(customerId, oneTimeLimit, dailyLimit, version);
    }

    void apply(long oneTimeLimit, long dailyLimit) {
        this.oneTimeLimit = oneTimeLimit;
        this.dailyLimit = dailyLimit;
    }
}
