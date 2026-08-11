package com.shinhan.corebank.autotransfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferCancelUseCase;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferChangeUseCase;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferQueryUseCase;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterUseCase;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * withIdempotency()의 release() 호출 범위를 순수 Mockito로 검증한다.
 * DB를 쓰는 통합테스트로는 "action()은 성공했는데 complete() 이후 단계가 실패하는" 상황을
 * 자연스럽게 재현하기 어려워서, Controller를 직접 생성해 IdempotencyService를 Mock으로 감싼다.
 */
@ExtendWith(MockitoExtension.class)
class AutoTransferControllerUnitTest {

    @Mock
    AutoTransferRegisterUseCase autoTransferRegisterUseCase;
    @Mock
    IdempotencyService idempotencyService;
    @Mock
    AutoTransferQueryUseCase autoTransferQueryUseCase;
    @Mock
    AutoTransferChangeUseCase autoTransferChangeUseCase;
    @Mock
    AutoTransferCancelUseCase autoTransferCancelUseCase;
    @Mock
    HttpServletRequest httpServletRequest;

    private AutoTransferController newController() {
        return new AutoTransferController(autoTransferRegisterUseCase, idempotencyService, new ObjectMapper(),
                autoTransferQueryUseCase, autoTransferChangeUseCase, autoTransferCancelUseCase);
    }

    private AutoTransfer sampleAutoTransfer() {
        return AutoTransfer.reconstitute(
                10L, 1L, 2L, "110987654321", "홍길동",
                10_000L, 1, 15,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(12), LocalDate.now().plusDays(10).plusDays(4),
                "내메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.now(), null, LocalDateTime.now());
    }

    private AutoTransferRegisterRequest sampleRegisterRequest() {
        return new AutoTransferRegisterRequest(1L, 2L, "110987654321", "홍길동", 10_000L, 1, 15,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(12), "내메모", "받는메모", "token");
    }

    @Test
    @DisplayName("action() 성공 후 complete()가 실패하면, 이미 성공한 처리인데도 release()가 호출되면 안 된다")
    void register_completeFailsAfterActionSucceeds_doesNotReleaseIdempotencyKey() {
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(idempotencyService.begin(eq("key-1"), eq(1L), any(), any())).thenReturn(IdempotencyResult.proceed());
        when(autoTransferRegisterUseCase.register(any())).thenReturn(sampleAutoTransfer());
        doThrow(new RuntimeException("complete 저장 중 장애"))
                .when(idempotencyService).complete(eq("key-1"), anyShort(), any());

        assertThatThrownBy(() -> newController().register("key-1", sampleRegisterRequest(), httpServletRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("complete 저장 중 장애");

        // action()(register)은 이미 성공했으므로, 같은 키로 재시도 시 중복 실행되지 않도록
        // release()는 절대 호출되면 안 된다
        verify(idempotencyService, never()).release(any());
    }

    @Test
    @DisplayName("action() 자체가 실패하면 release()가 호출된다")
    void register_actionFails_releasesIdempotencyKey() {
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(idempotencyService.begin(eq("key-2"), eq(1L), any(), any())).thenReturn(IdempotencyResult.proceed());
        when(autoTransferRegisterUseCase.register(any()))
                .thenThrow(new RuntimeException("등록 처리 중 실패"));

        assertThatThrownBy(() -> newController().register("key-2", sampleRegisterRequest(), httpServletRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("등록 처리 중 실패");

        verify(idempotencyService).release("key-2");
        verify(idempotencyService, never()).complete(any(), anyShort(), any());
    }
}
