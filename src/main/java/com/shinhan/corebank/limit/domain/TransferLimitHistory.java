package com.shinhan.corebank.limit.domain;

import lombok.Getter;

/**
 * 한도 변경 이력(REQ-TRSF-025). 변경 1건당 1행이 쌓이는 append-only 기록이며
 * 변경 직전 값만 담는다. 변경 후 값은 다음 이력의 변경 전 값이고, 마지막
 * 이력의 변경 후 값은 transfer_limit 의 현재값이라 따로 저장하지 않는다.
 * 변경자·요청 IP 같은 감사 정보는 audit_log 의 책임이다.
 */
@Getter
public class TransferLimitHistory {

    private final Long historyId;
    private final Long customerId;
    private final long beforeOneTimeLimit;
    private final long beforeDailyLimit;

    private TransferLimitHistory(Long historyId, Long customerId,
                                 long beforeOneTimeLimit, long beforeDailyLimit) {
        this.historyId = historyId;
        this.customerId = customerId;
        this.beforeOneTimeLimit = beforeOneTimeLimit;
        this.beforeDailyLimit = beforeDailyLimit;
    }

    /** 변경 직전 값으로 이력을 남긴다. */
    public static TransferLimitHistory create(Long customerId,
                                              long beforeOneTimeLimit, long beforeDailyLimit) {
        return new TransferLimitHistory(null, customerId, beforeOneTimeLimit, beforeDailyLimit);
    }

    public static TransferLimitHistory restore(Long historyId, Long customerId,
                                               long beforeOneTimeLimit, long beforeDailyLimit) {
        return new TransferLimitHistory(historyId, customerId, beforeOneTimeLimit, beforeDailyLimit);
    }
}
