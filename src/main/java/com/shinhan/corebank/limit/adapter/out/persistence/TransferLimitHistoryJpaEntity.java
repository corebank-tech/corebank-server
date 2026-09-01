package com.shinhan.corebank.limit.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer_limit_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferLimitHistoryJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long transferLimitHistoryId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 변경 직전 값. 변경 후 값은 다음 이력의 이 컬럼, 마지막이면 transfer_limit 의 현재값이다. */
    @Column(name = "before_one_time_limit", nullable = false)
    private long beforeOneTimeLimit;

    @Column(name = "before_daily_limit", nullable = false)
    private long beforeDailyLimit;

    private TransferLimitHistoryJpaEntity(TransferLimitHistory history) {
        this.transferLimitHistoryId = history.getTransferLimitHistoryId();
        this.customerId = history.getCustomerId();
        this.beforeOneTimeLimit = history.getBeforeOneTimeLimit();
        this.beforeDailyLimit = history.getBeforeDailyLimit();
    }

    static TransferLimitHistoryJpaEntity from(TransferLimitHistory history) {
        return new TransferLimitHistoryJpaEntity(history);
    }

    TransferLimitHistory toDomain() {
        return TransferLimitHistory.restore(transferLimitHistoryId, customerId, beforeOneTimeLimit, beforeDailyLimit);
    }
}
