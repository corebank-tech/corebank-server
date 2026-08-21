package com.shinhan.corebank.common.idempotency;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyJpaRepository repository;
    private static final String ANONYMOUS_SIGNUP_ENDPOINT =
            "POST /auth/signup/complete";
    private static final Pattern UUID_V4_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    @Transactional
    // 새 요청인지, 중복인지, 충돌인지, 재생해야하는지 판단
    public IdempotencyResult begin(String key, Long customerId, String endpoint, String requestBody) {
        if (!UUID_V4_PATTERN.matcher(key).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "Idempotency-Key는 UUID v4 형식이어야 합니다.");
        }
        // customerId 누락은 이후 INSERT에서 NOT NULL 제약 위반으로 터지는데,
        // 그 메시지에는 fk_idem_customer가 안 담겨서 DUPLICATE_REQUEST_IN_PROGRESS(409)로 잘못 응답하게 된다.
        // 요청 검증(toCommand())보다 begin()이 먼저 호출되므로 여기서 직접 막아야 한다.
        if (customerId == null
                && !ANONYMOUS_SIGNUP_ENDPOINT.equals(endpoint)) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING, "customerId는 필수입니다.");
        }
        String requestHash = sha256(requestBody);

        Optional<IdempotencyKeyJpaEntity> existing = repository.findById(key);
        if (existing.isEmpty()) {
            try {
                // saveAndFlush로 즉시 INSERT를 실행해, 동시에 같은 키로 먼저 들어온 요청이 있으면
                // PK 제약 위반이 이 자리에서 바로 터지게 한다(findById+save 사이 레이스 방지)
                repository.saveAndFlush(IdempotencyKeyJpaEntity.start(key, customerId, endpoint, requestHash, LocalDateTime.now()));
                return IdempotencyResult.proceed();
            } catch (DataIntegrityViolationException e) {
                // 원인이 둘로 갈린다: (1) 동시에 같은 키로 먼저 INSERT를 마친 요청 -> PK 충돌 -> 처리 중으로 응답
                //                  (2) 존재하지 않는 customerId -> fk_idem_customer 위반 -> 500 대신 입력값 오류로 응답
                String message = e.getMostSpecificCause().getMessage();
                if (message != null && message.contains("fk_idem_customer")) {
                    throw new BusinessException(CommonErrorCode.INVALID_INPUT, "존재하지 않는 고객입니다.");
                }
                throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
            }
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
    // 고객 생성 전 회원가입 완료 요청의 멱등 처리를 시작한다.
    public IdempotencyResult beginAnonymous(
            String key,
            String endpoint,
            String requestBody
    ) {
        if (!ANONYMOUS_SIGNUP_ENDPOINT.equals(endpoint)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "익명 멱등 처리를 지원하지 않는 API입니다."
            );
        }
        return begin(key, null, endpoint, requestBody);
    }

    @Transactional
    // 완료
    public void complete(String key, short httpStatus, String responseSnapshot) {
        repository.completeIfProcessing(key, httpStatus, responseSnapshot);
    }

    @Transactional
    // 고객 생성 전 예약한 익명 멱등키를 생성된 고객과 연결하면서 완료한다.
    public void completeAnonymous(
            String key,
            Long customerId,
            short httpStatus,
            String responseSnapshot
    ) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }
        int updated = repository.completeAnonymousIfProcessing(
                key,
                customerId,
                httpStatus,
                responseSnapshot
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "처리 중인 익명 멱등키를 완료하지 못했습니다."
            );
        }
    }

    @Transactional
    // 유스케이스 실행 중 실패해서 complete()까지 못 간 경우 — PROCESSING에 영구히 갇히지 않도록 예약을 해제한다.
    // 실패한 시도는 처리된 적이 없는 것으로 취급하므로, 상태 전이 대신 레코드 자체를 지운다.
    public void release(String key) {
        repository.deleteById(key);
    }

    // 문자열을 SHA-256 알고리즘으로 돌려서 64자리 16진수 문자열로 바꿔주는 유틸 메서드
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}

