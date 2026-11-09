package com.shinhan.corebank.auth.adapter.out.persistence;

import com.shinhan.corebank.auth.adapter.out.security.LoginIdBlindIndex;
import com.shinhan.corebank.auth.application.port.out.UnregisteredLoginFailurePort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 미가입 아이디의 로그인 실패를 아이디 HMAC 기준으로 마지막 실패 후 30일 동안 센다.
@Component
@RequiredArgsConstructor
public class UnregisteredLoginFailurePersistenceAdapter implements UnregisteredLoginFailurePort {

    private static final Duration FAILURE_RETENTION = Duration.ofDays(30);

    private final UnregisteredLoginFailureJpaRepository repository;
    private final LoginIdBlindIndex loginIdBlindIndex;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public int findActiveFailureCount(String normalizedUserId) {
        final String userIdHash = loginIdBlindIndex.hash(normalizedUserId);
        return repository
                .findActiveFailureCount(userIdHash, LocalDateTime.now(clock))
                .orElse(0);
    }

    // upsert가 잡은 행 잠금 안에서 다시 읽어야 동시 실패끼리 같은 횟수를 돌려받지 않는다.
    @Override
    @Transactional
    public int recordFailure(String normalizedUserId) {
        final String userIdHash = loginIdBlindIndex.hash(normalizedUserId);
        // DATETIME(6)에 맞춰 잘라야 만료 비교가 저장된 값과 같은 정밀도로 이뤄진다.
        final LocalDateTime failedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        repository.upsertFailure(userIdHash, failedAt, failedAt.plus(FAILURE_RETENTION));
        return repository.findFailureCount(userIdHash);
    }

    @Override
    @Transactional
    public int deleteExpired(int limit) {
        return repository.deleteExpiredBatch(LocalDateTime.now(clock), limit);
    }
}
