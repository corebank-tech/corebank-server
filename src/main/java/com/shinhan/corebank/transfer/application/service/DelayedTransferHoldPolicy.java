package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldPolicy;
import com.shinhan.corebank.transfer.application.port.out.DelayedTransferSettingPort;
import com.shinhan.corebank.transfer.config.DelayedTransferProperties;
import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 훅 C 지연이체 구현체(PH-06). 고객 락을 쥔 채 돌아 설정을 PK로 한 번만 읽는다. */
@Component
@RequiredArgsConstructor
public class DelayedTransferHoldPolicy implements TransferHoldPolicy {

    // 승인으로 푸는 FDS가 먼저 정하도록 뒤에 둔다(§9-5 규칙 4)
    static final int ORDER = 200;

    private final DelayedTransferProperties properties;
    private final DelayedTransferSettingPort delayedTransferSettingPort;

    @Override
    public int order() {
        return ORDER;
    }

    // 스위치를 조회보다 먼저 본다. 꺼져 있으면 즉시이체 경로에 쿼리가 늘지 않는다.
    @Override
    public TransferHoldDecision decide(TransferHoldContext context) {
        if (!properties.enabled()) {
            return TransferHoldDecision.proceed();
        }
        return delayedTransferSettingPort
                .findByCustomerId(context.customerId())
                .filter(setting -> setting.appliesTo(context.amount()))
                .map(setting -> TransferHoldDecision.holdUntil(
                        DelayedTransferSetting.POLICY_CODE, setting.releaseAt(context.requestedAt())))
                .orElseGet(TransferHoldDecision::proceed);
    }
}
