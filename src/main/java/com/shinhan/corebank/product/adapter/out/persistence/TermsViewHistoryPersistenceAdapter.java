package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.product.application.port.out.TermsView;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 상품 약관 열람 이력을 MySQL에 기록하고 열람 후 30분 동안 인정한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class TermsViewHistoryPersistenceAdapter implements TermsViewHistoryPort {

    private static final Duration VIEW_TTL = Duration.ofMinutes(30);

    private final TermsViewHistoryJpaRepository repository;
    private final Clock clock;

    @Override
    public TermsView record(Long customerId, Long termsId) {
        // DATETIME(6)에 맞춰 잘라야 record가 돌려준 값과 find가 읽은 값이 같다.
        LocalDateTime viewedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        LocalDateTime expiresAt = viewedAt.plus(VIEW_TTL);
        repository.upsert(customerId, termsId, viewedAt, expiresAt);
        return new TermsView(viewedAt, expiresAt);
    }

    @Override
    public Optional<TermsView> find(Long customerId, Long termsId) {
        return repository.findUsable(customerId, termsId, LocalDateTime.now(clock));
    }
}
