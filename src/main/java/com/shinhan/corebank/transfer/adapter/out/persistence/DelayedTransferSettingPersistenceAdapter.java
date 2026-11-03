package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.DelayedTransferSettingPort;
import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DelayedTransferSettingPersistenceAdapter implements DelayedTransferSettingPort {

    private final DelayedTransferSettingJpaRepository repository;

    @Override
    public Optional<DelayedTransferSetting> findByCustomerId(long customerId) {
        return repository.findById(customerId).map(DelayedTransferSettingMapper::toDomain);
    }

    @Override
    public Optional<DelayedTransferSetting> findForUpdateByCustomerId(long customerId) {
        return repository.findForUpdate(customerId).map(DelayedTransferSettingMapper::toDomain);
    }

    // 호출자 트랜잭션 안에서 부른다. 있는 행은 변경 감지로 바꾼다.
    @Override
    public DelayedTransferSetting save(DelayedTransferSetting setting, LocalDateTime now) {
        DelayedTransferSettingJpaEntity entity = repository
                .findById(setting.getCustomerId())
                .map(existing -> {
                    existing.change(setting.isEnabled(), setting.getThresholdAmount(), setting.getDelayHours(), now);
                    return existing;
                })
                .orElseGet(() -> repository.save(DelayedTransferSettingMapper.toNewEntity(setting, now)));
        return DelayedTransferSettingMapper.toDomain(entity);
    }
}
