package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DelayedTransferSettingMapper {

    public static DelayedTransferSetting toDomain(DelayedTransferSettingJpaEntity entity) {
        return DelayedTransferSetting.of(
                entity.getCustomerId(), entity.getEnabled(), entity.getThresholdAmount(), entity.getDelayHours());
    }

    public static DelayedTransferSettingJpaEntity toNewEntity(DelayedTransferSetting setting, LocalDateTime now) {
        return DelayedTransferSettingJpaEntity.builder()
                .customerId(setting.getCustomerId())
                .enabled(setting.isEnabled())
                .thresholdAmount(setting.getThresholdAmount())
                .delayHours(setting.getDelayHours())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
