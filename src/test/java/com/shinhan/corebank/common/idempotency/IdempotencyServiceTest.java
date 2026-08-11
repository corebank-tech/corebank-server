package com.shinhan.corebank.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verify(repository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("빈 문자열이면 CMN0001을 던진다")
    void begin_blankKey_throwsInvalidInput() {
        assertThatThrownBy(() -> idempotencyService.begin("", 1L, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
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
    @DisplayName("존재하지 않는 customerId면(FK 위반) 500 대신 CMN0001로 응답한다")
    void begin_nonexistentCustomerId_throwsInvalidInput() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        when(repository.findById(key)).thenReturn(Optional.empty());
        RuntimeException fkRootCause = new RuntimeException(
                "Cannot add or update a child row: a foreign key constraint fails "
                        + "(`corebank`.`idempotency_key`, CONSTRAINT `fk_idem_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`))");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert failed", fkRootCause));

        assertThatThrownBy(() -> idempotencyService.begin(key, 999_999_999L, "POST /auto-transfers", "{}"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
