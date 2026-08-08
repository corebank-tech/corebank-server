package com.shinhan.corebank.common.idempotency;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyJpaRepository repository;

    @Transactional
    // 새 요청인지, 중복인지, 충돌인지, 재생해야하는지 판단
    public IdempotencyResult begin(String key, Long customerId, String endpoint, String requestBody) {
        String requestHash = sha256(requestBody);

        Optional<IdempotencyKeyJpaEntity> existing = repository.findById(key);
        if (existing.isEmpty()) {
            repository.save(IdempotencyKeyJpaEntity.start(key, customerId, endpoint, requestHash, LocalDateTime.now()));
            return IdempotencyResult.proceed();
        }

        IdempotencyKeyJpaEntity found = existing.get();
        if (!found.matches(endpoint, requestHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST);
        }
        if (found.getState() == IdempotencyState.PROCESSING) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        }
        return IdempotencyResult.replay(found.getHttpStatus(), found.getResponseSnapshot());
    }

    @Transactional
    // 완료
    public void complete(String key, short httpStatus, String responseSnapshot) {
        repository.completeIfProcessing(key, httpStatus, responseSnapshot);
    }

    // 문자열을 SHA-256 알고리즘으로 돌려서 64자리 16진수 문자열로 바꿔주는 유틸 메서드
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}

