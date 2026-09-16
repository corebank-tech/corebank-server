package com.shinhan.corebank.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    IdempotencyKeyJpaRepository repository;

    @InjectMocks
    IdempotencyService idempotencyService;

    @Test
    @DisplayName("Idempotency-Key가 UUID v4 형식이 아니면 CMN0001을 던지고 DB를 조회하지 않는다")
    void begin_invalidKeyFormat_throwsInvalidInput() {
        assertThatThrownBy(() -> idempotencyService.begin("not-a-uuid", 1L, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));

        verify(repository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("빈 문자열이면 CMN0001을 던진다")
    void begin_blankKey_throwsInvalidInput() {
        assertThatThrownBy(() -> idempotencyService.begin("", 1L, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("동시에 같은 키로 먼저 INSERT된 경우(PK 충돌) 500 대신 처리 중으로 응답한다")
    void begin_concurrentInsertConflict_throwsDuplicateRequestInProgress() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        when(repository.findById(key)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> idempotencyService.begin(key, 1L, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS));
    }

    @Test
    @DisplayName("customerId가 null이면 INSERT 시도 없이 CMN0002를 던진다")
    void begin_nullCustomerId_throwsRequiredFieldMissing() {
        String key = "550e8400-e29b-41d4-a716-446655440000";

        assertThatThrownBy(() -> idempotencyService.begin(key, null, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        verify(repository, never()).findById(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("회원가입 완료는 고객 ID 없이 멱등키를 예약한다")
    void beginAnonymous_signupCompletion_allowsNullCustomerId() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        when(repository.findById(key)).thenReturn(Optional.empty());

        IdempotencyResult result = idempotencyService.beginAnonymous(key, "POST /auth/signup/complete", "{}");

        assertThat(result.replay()).isFalse();
        ArgumentCaptor<IdempotencyKeyJpaEntity> captor = ArgumentCaptor.forClass(IdempotencyKeyJpaEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 customerId면(FK 위반) 500 대신 CMN0001로 응답한다")
    void begin_nonexistentCustomerId_throwsInvalidInput() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        when(repository.findById(key)).thenReturn(Optional.empty());
        RuntimeException fkRootCause = new RuntimeException(
                "Cannot add or update a child row: a foreign key constraint fails "
                        + "(`corebank`.`idempotency_key`, CONSTRAINT `fk_idem_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`))");
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("insert failed", fkRootCause));

        assertThatThrownBy(() -> idempotencyService.begin(key, 999_999_999L, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("회원가입 완료에서 동일 키를 다른 요청에 사용하면 CMN0302를 던진다")
    void beginAnonymous_differentRequest_throwsKeyReuseConflict() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        String endpoint = "POST /auth/signup/complete";
        IdempotencyKeyJpaEntity existing = IdempotencyKeyJpaEntity.start(
                key, null, endpoint, sha256("{\"tempSignupToken\":\"TOKEN_A\"}"), LocalDateTime.now());
        when(repository.findById(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.beginAnonymous(key, endpoint, "{\"tempSignupToken\":\"TOKEN_B\"}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST));
    }

    @Test
    @DisplayName("회원가입 완료에서 동일 요청이 처리 중이면 CMN0301을 던진다")
    void beginAnonymous_sameRequestInProgress_throwsProcessing() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        String endpoint = "POST /auth/signup/complete";
        String request = "{\"tempSignupToken\":\"TOKEN_A\"}";
        IdempotencyKeyJpaEntity existing =
                IdempotencyKeyJpaEntity.start(key, null, endpoint, sha256(request), LocalDateTime.now());
        when(repository.findById(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.beginAnonymous(key, endpoint, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS));
    }

    @Test
    @DisplayName("익명 멱등 완료는 생성된 고객 ID와 응답을 함께 저장한다")
    void completeAnonymous_linksCreatedCustomer() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        when(repository.completeAnonymousIfProcessing(key, 101L, (short) 200, "{\"code\":\"0000\"}"))
                .thenReturn(1);

        idempotencyService.completeAnonymous(key, 101L, (short) 200, "{\"code\":\"0000\"}");

        verify(repository).completeAnonymousIfProcessing(key, 101L, (short) 200, "{\"code\":\"0000\"}");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
