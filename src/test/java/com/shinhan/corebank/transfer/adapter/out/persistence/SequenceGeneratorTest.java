package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("거래번호 채번기(SequenceGenerator) 통합 테스트")
class SequenceGeneratorTest extends IntegrationTestSupport {

    private static final LocalDate SEQ_DATE = LocalDate.of(2026, 8, 11);
    private static final TransferChannel CHANNEL = TransferChannel.WB;

    @Autowired
    private SequenceGenerator sequenceGenerator;

    @Autowired
    private TransactionSequenceJpaRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteById(new TransactionSequenceId(SEQ_DATE, CHANNEL.name()));
    }

    @Test
    @DisplayName("당일 최초 채번 시 1부터 시작하는 20자리 거래번호를 반환한다")
    void firstCallOfTheDayStartsFromOne() {
        // when
        String transactionNumber = sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);

        // then
        String expectedDatePart = SEQ_DATE.format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(transactionNumber).hasSize(20);
        assertThat(transactionNumber).isEqualTo(expectedDatePart + "WB" + "0000000001");
    }

    @Test
    @DisplayName("연속 호출 시 일련번호가 1씩 증가한다")
    void sequentialCallsIncrementByOne() {
        // when
        String first = sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
        String second = sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);

        // then
        assertThat(first).endsWith("0000000001");
        assertThat(second).endsWith("0000000002");
    }

    @Test
    @DisplayName("seqDate가 null이면 즉시 NullPointerException을 던진다")
    void nextTransactionNumber_throwsNpe_whenSeqDateIsNull() {
        assertThatThrownBy(() -> sequenceGenerator.nextTransactionNumber(null, CHANNEL))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("seqDate");
    }

    @Test
    @DisplayName("channel이 null이면 즉시 NullPointerException을 던진다")
    void nextTransactionNumber_throwsNpe_whenChannelIsNull() {
        assertThatThrownBy(() -> sequenceGenerator.nextTransactionNumber(SEQ_DATE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel");
    }

    @Test
    @DisplayName("일련번호가 10자리 상한(9999999999)을 초과하면 BusinessException(TRF9002)을 던진다")
    void nextTransactionNumber_throwsBusinessException_whenSequenceExceedsTenDigits() {
        // given: 이미 상한에 도달한 채번 행
        repository.saveAndFlush(
                TransactionSequenceJpaEntity.builder()
                        .seqDate(SEQ_DATE)
                        .channel(CHANNEL.name())
                        .lastSeq(9_999_999_999L)
                        .updatedAt(LocalDateTime.now(ZoneOffset.UTC))
                        .build()
        );

        // when & then
        assertThatThrownBy(() -> sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TransferErrorCode.TRANSACTION_SEQUENCE_EXHAUSTED);
    }

    @Test
    @DisplayName("JVM 기본 시간대가 UTC가 아니어도 updated_at은 UTC 기준으로 저장된다")
    void updatedAt_isStoredInUtc_regardlessOfJvmDefaultTimeZone() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            // given
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

            // when
            sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);

            // then
            TransactionSequenceJpaEntity entity = repository
                    .findById(new TransactionSequenceId(SEQ_DATE, CHANNEL.name()))
                    .orElseThrow();
            assertThat(entity.getUpdatedAt())
                    .isCloseTo(LocalDateTime.now(ZoneOffset.UTC), org.assertj.core.api.Assertions.within(10, ChronoUnit.SECONDS));
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    @DisplayName("당일 최초 채번 순간을 포함해 동시에 50건을 요청해도 중복 없는 연속 순번이 보장된다")
    void issuesUniqueSequenceNumbersUnderConcurrency() throws Exception {
        int requestCount = 50;
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            // given
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    return sequenceGenerator.nextTransactionNumber(SEQ_DATE, CHANNEL);
                }));
            }

            // when
            startLatch.countDown();
            List<String> transactionNumbers = new ArrayList<>();
            for (Future<String> future : futures) {
                transactionNumbers.add(future.get(60, TimeUnit.SECONDS));
            }

            // then
            assertThat(transactionNumbers)
                    .hasSize(requestCount)
                    .doesNotHaveDuplicates();

            String datePart = SEQ_DATE.format(DateTimeFormatter.BASIC_ISO_DATE);
            List<String> expectedNumbers = IntStream.rangeClosed(1, requestCount)
                    .mapToObj(seq -> datePart + "WB" + String.format("%010d", seq))
                    .collect(Collectors.toList());

            assertThat(transactionNumbers)
                    .containsExactlyInAnyOrderElementsOf(expectedNumbers);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
