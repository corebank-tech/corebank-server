package com.shinhan.corebank.limit.adapter.out.persistence;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferLimitPersistenceAdapter implements TransferLimitQueryPort, TransferLimitCommandPort {

    private final TransferLimitJpaRepository limitRepository;
    private final TransferLimitDailyUsageJpaRepository usageRepository;
    /** 네이티브 INSERT 는 JPA Auditing 을 타지 않아 시각을 직접 채워야 한다. */
    private final Clock clock;

    @Override
    public Optional<TransferLimit> findByCustomerId(Long customerId) {
        return limitRepository.findById(customerId).map(TransferLimitJpaEntity::toDomain);
    }

    @Override
    public Optional<TransferLimitDailyUsage> findUsage(Long customerId, LocalDate usageDate) {
        return usageRepository.findById(new TransferLimitDailyUsageId(customerId, usageDate))
                .map(TransferLimitDailyUsageJpaEntity::toDomain);
    }

    @Override
    public Optional<TransferLimit> findForUpdateByCustomerId(Long customerId) {
        return limitRepository.findForUpdateByCustomerId(customerId).map(TransferLimitJpaEntity::toDomain);
    }

    @Override
    public Optional<TransferLimit> findForShareByCustomerId(Long customerId) {
        return limitRepository.findForShareByCustomerId(customerId).map(TransferLimitJpaEntity::toDomain);
    }

    /** 호출자가 findForUpdateByCustomerId 로 잠근 행에만 쓴다. 그래서 없는 행을 만들지 않는다. */
    @Override
    public TransferLimit update(TransferLimit limit) {
        limitRepository.updateLimit(limit.getCustomerId(), limit.getOneTimeLimit(),
                limit.getDailyLimit(), LocalDateTime.now(clock));
        return limit;
    }

    @Override
    public void saveIfAbsent(TransferLimit limit) {
        limitRepository.insertIfAbsent(limit.getCustomerId(), limit.getOneTimeLimit(),
                limit.getDailyLimit(), LocalDateTime.now(clock));
    }

    /**
     * 행을 먼저 보장한 뒤 잠근다. 순서를 바꾸면 그날 첫 이체에서 잠글 대상이 없어 동시 요청이
     * 서로를 못 보고 지나간다 - 자세한 이유는 insertIfAbsent 주석에 있다.
     */
    @Override
    public TransferLimitDailyUsage lockDailyUsage(Long customerId, LocalDate usageDate) {
        usageRepository.insertIfAbsent(customerId, usageDate, LocalDateTime.now(clock));
        // 바로 위에서 행을 보장했으므로 비어 있을 수 없다. Optional 을 푸는 것뿐이다.
        return usageRepository.findForUpdate(customerId, usageDate)
                .map(TransferLimitDailyUsageJpaEntity::toDomain)
                .orElseThrow();
    }

    @Override
    public void saveUsage(TransferLimitDailyUsage usage) {
        // lockDailyUsage 가 행을 보장하고 영속성 컨텍스트에 올려 둔 뒤에만 호출된다.
        TransferLimitDailyUsageJpaEntity entity = usageRepository
                .findById(new TransferLimitDailyUsageId(usage.getCustomerId(), usage.getUsageDate()))
                .orElseThrow();
        entity.apply(usage.getUsedAmount());
        usageRepository.save(entity);
    }
}
