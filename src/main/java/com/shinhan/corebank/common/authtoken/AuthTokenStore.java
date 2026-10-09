package com.shinhan.corebank.common.authtoken;

import static java.util.stream.Collectors.toSet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 도메인 토큰 어댑터들이 공유하는 MySQL 토큰 저장소 (#580).
 *
 * 저장과 소비 모두 호출자 트랜잭션에 참여한다(REQUIRED). 업무가 롤백되면 소비도 롤백돼
 * 같은 토큰으로 다시 시도할 수 있다(#498). 트랜잭션 밖에서 부르면 메서드 단위로 커밋된다.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenStore {

    private final AuthTokenJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void save(AuthTokenPurpose purpose, String token, Long customerId, Object payload, Duration ttl) {
        Objects.requireNonNull(token);
        LocalDateTime now = LocalDateTime.now(clock);
        repository.save(
                AuthTokenJpaEntity.issue(purpose, hash(token), customerId, serialize(payload), now, now.plus(ttl)));
    }

    @Transactional(readOnly = true)
    public <T> Optional<T> find(AuthTokenPurpose purpose, String token, Class<T> payloadType) {
        if (isBlank(token)) {
            return Optional.empty();
        }
        return repository
                .findUsablePayload(hash(token), purpose, LocalDateTime.now(clock))
                .map(json -> deserialize(json, payloadType));
    }

    @Transactional
    public <T> Optional<T> consume(AuthTokenPurpose purpose, String token, Class<T> payloadType) {
        if (isBlank(token)) {
            return Optional.empty();
        }
        String tokenHash = hash(token);
        if (repository.consume(tokenHash, purpose, LocalDateTime.now(clock)) != 1) {
            return Optional.empty();
        }
        return repository.findPayload(tokenHash, purpose).map(json -> deserialize(json, payloadType));
    }

    // Redis Lua의 문자열 비교와 같다. payload 컬럼이 utf8mb4_bin이라 대소문자까지 구분한다.
    @Transactional
    public boolean consumeIfMatches(AuthTokenPurpose purpose, String token, Object expectedPayload) {
        if (isBlank(token)) {
            return false;
        }
        return repository.consumeIfPayloadMatches(
                        hash(token), purpose, serialize(expectedPayload), LocalDateTime.now(clock))
                == 1;
    }

    @Transactional
    public boolean consumeIfCustomerMatches(AuthTokenPurpose purpose, String token, Long customerId) {
        if (isBlank(token) || customerId == null) {
            return false;
        }
        return repository.consumeIfCustomerMatches(hash(token), purpose, customerId, LocalDateTime.now(clock)) == 1;
    }

    // 원본 토큰을 모두 소비하고 새 토큰을 발급한다. 원본이 하나라도 없으면 아무것도 바꾸지 않는다(회원가입 Lua 대체).
    @Transactional
    public boolean transition(
            List<Source> sources, AuthTokenPurpose purpose, String newToken, Object payload, Duration ttl) {
        if (sources.isEmpty() || sources.stream().anyMatch(source -> isBlank(source.token()))) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Key> sourceKeys = sources.stream()
                .map(source -> new Key(source.purpose(), hash(source.token())))
                .collect(toSet());
        Set<Key> lockedKeys = repository
                .lockUsable(sourceKeys.stream().map(Key::tokenHash).toList(), now)
                .stream()
                .map(entity -> new Key(entity.getPurpose(), entity.getTokenHash()))
                .collect(toSet());
        if (!lockedKeys.containsAll(sourceKeys)) {
            return false;
        }
        sourceKeys.forEach(key -> repository.consume(key.tokenHash(), key.purpose(), now));
        try {
            repository.saveAndFlush(
                    AuthTokenJpaEntity.issue(purpose, hash(newToken), null, serialize(payload), now, now.plus(ttl)));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("발급할 인증 토큰이 이미 있습니다.", exception);
        }
        return true;
    }

    // 다른 요청이 쓰지 못하게 선점한다. 선점된 토큰은 조회·소비되지 않는다.
    @Transactional
    public <T> Optional<T> claim(AuthTokenPurpose purpose, String token, String claimId, Class<T> payloadType) {
        if (isBlank(token) || isBlank(claimId)) {
            return Optional.empty();
        }
        String tokenHash = hash(token);
        if (repository.claim(tokenHash, purpose, claimId, LocalDateTime.now(clock)) != 1) {
            return Optional.empty();
        }
        return repository.findPayload(tokenHash, purpose).map(json -> deserialize(json, payloadType));
    }

    @Transactional
    public boolean completeClaim(AuthTokenPurpose purpose, String token, String claimId) {
        if (isBlank(token) || isBlank(claimId)) {
            return false;
        }
        return repository.completeClaim(hash(token), purpose, claimId, LocalDateTime.now(clock)) == 1;
    }

    @Transactional
    public boolean releaseClaim(AuthTokenPurpose purpose, String token, String claimId) {
        if (isBlank(token) || isBlank(claimId)) {
            return false;
        }
        return repository.releaseClaim(hash(token), purpose, claimId, LocalDateTime.now(clock)) == 1;
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("인증 토큰 payload 직렬화에 실패했습니다.", exception);
        }
    }

    private <T> T deserialize(String json, Class<T> payloadType) {
        try {
            return objectMapper.readValue(json, payloadType);
        } catch (JacksonException exception) {
            throw new IllegalStateException("인증 토큰 payload 역직렬화에 실패했습니다.", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Source(AuthTokenPurpose purpose, String token) {}

    private record Key(AuthTokenPurpose purpose, String tokenHash) {}
}
