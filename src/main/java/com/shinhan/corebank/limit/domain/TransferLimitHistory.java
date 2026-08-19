package com.shinhan.corebank.limit.domain;

import lombok.Getter;

/**
 * 한도 변경 이력(REQ-TRSF-025). 변경 1건당 1행이 쌓이는 append-only 기록이며
 * 값의 전후 변화만 담는다. 변경자·요청 IP 같은 감사 정보는 audit_log 의 책임이다.
 */
@Getter
public class TransferLimitHistory {

    private final Long historyId;
    private final Long customerId;
    private final long beforeOneTimeLimit;
    private final long afterOneTimeLimit;
    private final long beforeDailyLimit;
    private final long afterDailyLimit;

    private TransferLimitHistory(Long historyId, Long customerId,
                                 long beforeOneTimeLimit, long afterOneTimeLimit,
                                 long beforeDailyLimit, long afterDailyLimit) {
        this.historyId = historyId;
        this.customerId = customerId;
        this.beforeOneTimeLimit = beforeOneTimeLimit;
        this.afterOneTimeLimit = afterOneTimeLimit;
        this.beforeDailyLimit = beforeDailyLimit;
        this.afterDailyLimit = afterDailyLimit;
    }

    /** 변경 직전 값과 변경 후 값으로 이력을 남긴다. */
    public static TransferLimitHistory create(Long customerId,
                                              long beforeOneTimeLimit, long afterOneTimeLimit,
                                              long beforeDailyLimit, long afterDailyLimit) {
        return new TransferLimitHistory(null, customerId,
                beforeOneTimeLimit, afterOneTimeLimit, beforeDailyLimit, afterDailyLimit);
    }

    public static TransferLimitHistory restore(Long historyId, Long customerId,
                                                    long beforeOneTimeLimit, long afterOneTimeLimit,
                                                    long beforeDailyLimit, long afterDailyLimit) {
        return new TransferLimitHistory(historyId, customerId,
                beforeOneTimeLimit, afterOneTimeLimit, beforeDailyLimit, afterDailyLimit);
    }
}
