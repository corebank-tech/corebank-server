package com.shinhan.corebank.limit.domain;

import java.time.LocalDate;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;

import lombok.Getter;

/**
 * 고객·일자 단위 한도 사용액. usage_date 는 KST 영업일 기준이며, 정상 확정된
 * 이체만 합산한다(REQ-TRSF-011). 서버·DB 가 KST 로 동작하므로(REQ-NFR-018)
 * 시간대 변환 없이 Clock 이 주는 일자를 그대로 쓴다.
 */
@Getter
public class TransferLimitDailyUsage {

    private final Long customerId;
    private final LocalDate usageDate;
    private long usedAmount;

    private TransferLimitDailyUsage(Long customerId, LocalDate usageDate, long usedAmount) {
        this.customerId = customerId;
        this.usageDate = usageDate;
        this.usedAmount = usedAmount;
    }

    /** 해당 일자의 첫 이체 시 사용액 0 으로 시작한다. */
    public static TransferLimitDailyUsage create(Long customerId, LocalDate usageDate) {
        return new TransferLimitDailyUsage(customerId, usageDate, 0L);
    }

    public static TransferLimitDailyUsage restore(Long customerId, LocalDate usageDate, long usedAmount) {
        return new TransferLimitDailyUsage(customerId, usageDate, usedAmount);
    }

    /**
     * 확정된 이체 금액을 당일 누적에 더한다. 사용액은 누적 카운터라 감소하지 않는다.
     */
    public void add(long amount) {
        if (amount < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "이체 금액은 음수일 수 없습니다.");
        }
        this.usedAmount = Math.addExact(this.usedAmount, amount);
    }

    /** 1일 한도 대비 잔여 이체가능금액. 음수가 되지 않도록 0 으로 막는다. */
    public long remainingAgainst(long dailyLimit) {
        return Math.max(0L, dailyLimit - usedAmount);
    }
}
