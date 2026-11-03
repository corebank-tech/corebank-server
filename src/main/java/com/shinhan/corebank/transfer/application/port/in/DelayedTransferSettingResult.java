package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;

public record DelayedTransferSettingResult(boolean enabled, long thresholdAmount, int delayHours) {

    public static DelayedTransferSettingResult from(DelayedTransferSetting setting) {
        return new DelayedTransferSettingResult(
                setting.isEnabled(), setting.getThresholdAmount(), setting.getDelayHours());
    }
}
