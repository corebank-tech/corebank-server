package com.shinhan.corebank.fds.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService.Accepted;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService.Batch;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 고객 행동 이벤트 제출 계약을 Swagger와 HTTP API로 노출한다. */
@RestController
@RequestMapping("/fds/behavior-flows")
@Tag(name = "FDS 행동 이벤트", description = "로그인 고객의 이체 행동 흐름")
@RequiredArgsConstructor
public class BehaviorFlowController {

    private final BehaviorFlowService service;
    private final CurrentCustomerProvider currentCustomer;
    private final IdempotentRequestExecutor idempotency;
    private final ObjectMapper mapper;

    @PostMapping("/{flowId}/events")
    @Operation(
            operationId = "submitFdsBehaviorEvents",
            summary = "행동 이벤트 제출",
            description =
                    "FE가 생성한 flowId를 최초 제출 고객에게 귀속합니다. 한 flow는 최초 수신부터 30분, 최대 100개와 요청 본문 32 KiB 제한을 적용하며 동일 이벤트 재전송은 멱등 처리하고 충돌·종료 후 추가 제출은 거부합니다.")
    public ResponseEntity<ApiResponse<Accepted>> submit(
            @PathVariable UUID flowId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = Batch.class)))
                    @RequestBody
                    String rawBody) {
        if (rawBody.getBytes(StandardCharsets.UTF_8).length > 32 * 1024) {
            throw new BusinessException(FdsErrorCode.INVALID_EVENT);
        }
        Batch batch;
        try {
            batch = mapper.readValue(rawBody, Batch.class);
        } catch (JacksonException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        long customerId = currentCustomer.getCurrentCustomerId();
        // 동일 키의 응답은 공용 멱등 저장소에서 재생하고, 다른 키의 이벤트 중복은 flow 행에서 판정한다.
        return idempotency.execute(
                idempotencyKey,
                customerId,
                "POST /fds/behavior-flows/{flowId}/events",
                new EventFingerprint(flowId, batch),
                new TypeReference<>() {},
                () -> ApiResponse.success(service.submit(flowId, customerId, batch)));
    }

    private record EventFingerprint(UUID flowId, Batch batch) {}
}
