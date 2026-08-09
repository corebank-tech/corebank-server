package com.shinhan.corebank.autotransfer.adapter.in.web;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterUseCase;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auto-transfers")
@RequiredArgsConstructor
public class AutoTransferController {
    private final AutoTransferRegisterUseCase autoTransferRegisterUseCase;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    // 멱등성 확인 후, 재요청 -> 저장된 응답, 신규 요청 -> 등록
    public ResponseEntity<ApiResponse<AutoTransferResponse>> register (@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                       @RequestBody AutoTransferRegisterRequest request) {
        IdempotencyResult idempotencyResult = idempotencyService.begin(idempotencyKey, request.customerId(),
                "POST /auto-transfers", toJson(request));
        if(idempotencyResult.replay()) {
            return ResponseEntity.status(idempotencyResult.httpStatus()).body(fromJson(idempotencyResult.responseSnapshot()));
        }

        AutoTransfer autoTransfer = autoTransferRegisterUseCase.register(request.toCommand());
        ApiResponse<AutoTransferResponse> response = ApiResponse.success(AutoTransferResponse.from(autoTransfer));
        idempotencyService.complete(idempotencyKey, (short) HttpStatus.OK.value(), toJson(response));

        return ResponseEntity.ok(response);
    }

    // 멱등키 시작·완료 시점에 요청/응답 저장
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("요청/응답을 JSON으로 직렬화하지 못했습니다.", e);
        }
    }

    // 재요청 시 저장된 응답 스냅샷을 그대로 복원
    private ApiResponse<AutoTransferResponse> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>()
            {});
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 응답을 역직렬화하지 못했습니다.", e);
        }
    }
}
