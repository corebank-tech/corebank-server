package com.shinhan.corebank.otp.adapter.out.persistence;

import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.otp.application.port.out.OtpIssueLockPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객별 OTP 발급 잠금을 MySQL 행 하나로 잡는다 (#580).
 *
 * 호출자 트랜잭션을 내려놓고 문장마다 바로 커밋해 다른 인스턴스에 즉시 보이게 한다.
 * 이미 잡힌 잠금은 기다리지 않고 빈 값을 돌려준다 — 대기가 DB 커넥션 점유로 번지지 않게 하려는 것이다.
 */
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class OtpIssueLockPersistenceAdapter implements OtpIssueLockPort {

    private final OtpIssueLockJpaRepository repository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> tryAcquire(Long customerId, Duration ttl) {
        String ownerId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(ttl);
        boolean acquired = repository.insertIfAbsent(customerId, ownerId, expiresAt) == 1
                || repository.takeOverIfExpired(customerId, ownerId, expiresAt, now) == 1;
        return acquired ? Optional.of(ownerId) : Optional.empty();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void release(Long customerId, String ownerId) {
        // 소유권이 이미 만료됐거나 바뀐 잠금은 지우지 않는다.
        repository.deleteByOwner(customerId, ownerId);
    }
}
