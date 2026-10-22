package com.shinhan.corebank.subscription.api;

import com.shinhan.corebank.transfer.api.DepositChannel;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 이자 지급 요청.
 *
 * 계산 조건은 호출자가 검증한 가입 정보와 해지 정책에서 전달한다.
 * 이율은 퍼센트 단위이며, 계산 기간은 [openedDate, toExclusive)이다.
 */
public record InterestPaymentCommand(
        Long accountId,
        LocalDate openedDate,
        LocalDate toExclusive,
        BigDecimal annualRate,
        CalculationMode calculationMode,
        DepositChannel channel,
        InterestPaymentReason reason) {

    public enum CalculationMode {
        SIMPLE,
        MONTHLY_COMPOUND
    }
}
