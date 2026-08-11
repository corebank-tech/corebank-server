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
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.PlatformTransactionManager;

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
}
