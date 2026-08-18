package com.shinhan.corebank.common.idempotency;

import java.util.function.Supplier;

import com.shinhan.corebank.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// 멱등키 처리 5단계(시작 → 재생-또는-진행 → 실행 → 완료 → 실패 시 예약 해제)를 한 곳에 모은 공용 헬퍼.
// TransferController/AutoTransferController가 각자 들고 있던 동일 로직을 여기로 합쳤다.
@Component
@RequiredArgsConstructor
public class IdempotentRequestExecutor {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public <T> ResponseEntity<ApiResponse<T>> execute(
            String idempotencyKey, Long customerId, String endpoint, Object fingerprint,
            TypeReference<ApiResponse<T>> responseType, Supplier<ApiResponse<T>> action) {
        IdempotencyResult idempotencyResult = idempotencyService.begin(idempotencyKey, customerId, endpoint, toJson(fingerprint));
        if (idempotencyResult.replay()) {
            return ResponseEntity.status(idempotencyResult.httpStatus())
                    .body(fromJson(idempotencyResult.responseSnapshot(), responseType));
        }
        ApiResponse<T> response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            // action() 자체가 실패했을 때만 예약을 해제한다 — 이미 성공한 뒤(complete() 등)에서 실패하면 여기서 release()를 타면 안 된다.
            // 그러면 이미 커밋된 작업인데 같은 Idempotency-Key로 재시도 시 새로 처리(중복 실행)돼 버린다.
            idempotencyService.release(idempotencyKey);
            throw e;
        }
        idempotencyService.complete(idempotencyKey, (short) HttpStatus.OK.value(), toJson(response));
        return ResponseEntity.ok(response);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("요청/응답을 JSON으로 직렬화하지 못했습니다.", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 응답을 역직렬화하지 못했습니다.", e);
        }
    }
}
