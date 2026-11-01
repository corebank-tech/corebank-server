package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDateTime;

/**
 * 고객별 지연이체 설정(PH-06). 근거는 전자금융거래법 제13조제2항·시행령 제9조의2다.
 *
 * 최소 3시간은 금감원 안내·은행 공통 기준이고 최대 24시간은 우리 기준이다. 기본 기준금액 100만 원은 은행들의 건당 100만 원 이하 즉시이체 예외에 맞췄다.
 */
public class DelayedTransferSetting {

    public static final String POLICY_CODE = "DELAYED_TRANSFER";
    public static final int MIN_DELAY_HOURS = 3;
    public static final int MAX_DELAY_HOURS = 24;
    public static final long DEFAULT_THRESHOLD_AMOUNT = 1_000_000L;

    private final long customerId;
    private final boolean enabled;
    private final long thresholdAmount;
    private final int delayHours;

    private DelayedTransferSetting(long customerId, boolean enabled, long thresholdAmount, int delayHours) {
        this.customerId = customerId;
        this.enabled = enabled;
        this.thresholdAmount = thresholdAmount;
        this.delayHours = delayHours;
    }

    public static DelayedTransferSetting of(long customerId, boolean enabled, long thresholdAmount, int delayHours) {
        if (thresholdAmount <= 0 || delayHours < MIN_DELAY_HOURS || delayHours > MAX_DELAY_HOURS) {
            throw new BusinessException(TransferErrorCode.INVALID_DELAYED_TRANSFER_SETTING);
        }
        return new DelayedTransferSetting(customerId, enabled, thresholdAmount, delayHours);
    }

    /** 저장된 설정이 없는 고객. 꺼져 있고, 켤 때 쓸 기본값을 담는다. */
    public static DelayedTransferSetting defaultsFor(long customerId) {
        return new DelayedTransferSetting(customerId, false, DEFAULT_THRESHOLD_AMOUNT, MIN_DELAY_HOURS);
    }

    public boolean appliesTo(long amount) {
        return enabled && amount >= thresholdAmount;
    }

    public LocalDateTime releaseAt(LocalDateTime requestedAt) {
        return requestedAt.plusHours(delayHours);
    }

    /** 보호를 약하게 하는 변경인지. 사기범이 먼저 노리는 동작이라 추가 인증을 받는다. */
    public boolean loosens(DelayedTransferSetting previous) {
        if (!previous.enabled) {
            return false;
        }
        return !enabled || thresholdAmount > previous.thresholdAmount || delayHours < previous.delayHours;
    }

    public long getCustomerId() {
        return customerId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getThresholdAmount() {
        return thresholdAmount;
    }

    public int getDelayHours() {
        return delayHours;
    }
}
