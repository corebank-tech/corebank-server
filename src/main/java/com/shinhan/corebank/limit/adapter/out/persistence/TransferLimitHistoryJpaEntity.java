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
    private Long historyId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "before_one_time_limit", nullable = false)
    private long beforeOneTimeLimit;

    @Column(name = "after_one_time_limit", nullable = false)
    private long afterOneTimeLimit;

    @Column(name = "before_daily_limit", nullable = false)
    private long beforeDailyLimit;

    @Column(name = "after_daily_limit", nullable = false)
    private long afterDailyLimit;

    private TransferLimitHistoryJpaEntity(TransferLimitHistory history) {
        this.historyId = history.getHistoryId();
        this.customerId = history.getCustomerId();
        this.beforeOneTimeLimit = history.getBeforeOneTimeLimit();
        this.afterOneTimeLimit = history.getAfterOneTimeLimit();
        this.beforeDailyLimit = history.getBeforeDailyLimit();
        this.afterDailyLimit = history.getAfterDailyLimit();
    }

    static TransferLimitHistoryJpaEntity from(TransferLimitHistory history) {
        return new TransferLimitHistoryJpaEntity(history);
    }

    TransferLimitHistory toDomain() {
        return TransferLimitHistory.restore(historyId, customerId,
                beforeOneTimeLimit, afterOneTimeLimit, beforeDailyLimit, afterDailyLimit);
    }
}
