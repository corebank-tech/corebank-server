package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;

import com.shinhan.corebank.transfer.domain.TransferChannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SequenceGenerator 재시도 예외 범위 단위 테스트")
class SequenceGeneratorRetryTest {

    private static final LocalDate SEQ_DATE = LocalDate.of(2026, 8, 11);
    private static final TransferChannel CHANNEL = TransferChannel.WB;

    @Mock
    private TransactionSequenceJpaRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private SequenceGenerator sequenceGenerator;

    @Test
    @DisplayName("재시도 대상이 아닌 DataAccessException은 재시도 없이 즉시 전파된다")
    void nonRetryableException_propagatesImmediately_withoutRetry() {
        // given
        when(repository.findBySeqDateAndChannelForUpdate(SEQ_DATE, CHANNEL.name()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any()))
                .thenThrow(new InvalidDataAccessResourceUsageException("SQL 문법 오류"));

        // when & then
        assertThatThrownBy(() -> sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL))
                .isInstanceOf(InvalidDataAccessResourceUsageException.class);

        verify(repository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("재시도 가능한 예외가 반복되면 재시도 사이에 backoff 지연이 발생한다")
    void retryableFailures_incurBackoffDelayBetweenAttempts() {
        // given: 첫 4번은 PK 경합으로 실패, 5번째에 성공
        when(repository.findBySeqDateAndChannelForUpdate(SEQ_DATE, CHANNEL.name()))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("PK 충돌"))
                .thenThrow(new DataIntegrityViolationException("PK 충돌"))
                .thenThrow(new DataIntegrityViolationException("PK 충돌"))
                .thenThrow(new DataIntegrityViolationException("PK 충돌"))
                .thenReturn(null);

        // when
        long start = System.nanoTime();
        sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // then: backoff 없이는 4번의 재시도가 수 ms 안에 끝나므로, 최소 지연이 있었는지로 backoff 적용 여부를 검증
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(30);
    }
}
