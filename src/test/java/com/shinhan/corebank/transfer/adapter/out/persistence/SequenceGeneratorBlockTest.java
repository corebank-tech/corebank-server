package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("거래번호 묶음 채번 (#786)")
class SequenceGeneratorBlockTest extends IntegrationTestSupport {

    private static final int BLOCK_SIZE = 100;
    private static final LocalDate SEQ_DATE = LocalDate.of(2099, 2, 1);
    private static final LocalDate NEXT_DATE = LocalDate.of(2099, 2, 2);
    private static final TransferChannel CHANNEL = TransferChannel.WB;
    private static final long MAX_SEQUENCE = 9_999_999_999L;

    @Autowired
    private TransactionSequenceJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        repository.deleteById(new TransactionSequenceId(SEQ_DATE, CHANNEL.name()));
        repository.deleteById(new TransactionSequenceId(NEXT_DATE, CHANNEL.name()));
    }

    @Test
    @DisplayName("150건을 받으면 번호는 1~150으로 이어지고, 행은 두 번만 예약돼 last_seq가 200이다")
    void issuesContiguousNumbersAndReservesPerBlock() {
        SequenceGenerator generator = newServer();

        List<Long> seqs = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            seqs.add(seqOf(generator.nextTransactionNumber(SEQ_DATE, CHANNEL)));
        }

        assertThat(seqs).first().isEqualTo(1L);
        assertThat(seqs).last().isEqualTo(150L);
        assertThat(seqs).isSorted().doesNotHaveDuplicates();
        assertThat(lastSeq(SEQ_DATE)).isEqualTo(200L);
    }

    @Test
    @DisplayName("채번기 두 개가 동시에 300건씩 받아도 번호가 겹치지 않고 모두 예약된 범위 안에 있다")
    void twoServersNeverShareNumbers() throws Exception {
        List<SequenceGenerator> servers = List.of(newServer(), newServer());
        int callsPerServer = 300;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();
            for (SequenceGenerator server : servers) {
                for (int i = 0; i < callsPerServer; i++) {
                    futures.add(executor.submit(() -> {
                        startLatch.await();
                        return server.nextTransactionNumber(SEQ_DATE, CHANNEL);
                    }));
                }
            }
            startLatch.countDown();
            List<Long> seqs = new ArrayList<>();
            for (Future<String> future : futures) {
                seqs.add(seqOf(future.get(60, TimeUnit.SECONDS)));
            }

            assertThat(seqs).hasSize(callsPerServer * 2).doesNotHaveDuplicates();
            assertThat(seqs).allSatisfy(seq -> assertThat(seq).isBetween(1L, lastSeq(SEQ_DATE)));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("재시작한 서버는 다음 묶음부터 받는다. 쓰지 않은 번호는 빈칸으로 남고 겹치지 않는다")
    void restartedServerSkipsUnusedNumbers() {
        String beforeRestart = newServer().nextTransactionNumber(SEQ_DATE, CHANNEL);

        String afterRestart = newServer().nextTransactionNumber(SEQ_DATE, CHANNEL);

        assertThat(seqOf(beforeRestart)).isEqualTo(1L);
        assertThat(seqOf(afterRestart)).isEqualTo(101L);
    }

    @Test
    @DisplayName("날짜가 바뀌면 그 날짜의 묶음을 따로 받아 1부터 시작한다")
    void eachDateHasItsOwnBlock() {
        SequenceGenerator generator = newServer();
        generator.nextTransactionNumber(SEQ_DATE, CHANNEL);
        generator.nextTransactionNumber(SEQ_DATE, CHANNEL);

        String nextDay = generator.nextTransactionNumber(NEXT_DATE, CHANNEL);
        String sameDayAgain = generator.nextTransactionNumber(SEQ_DATE, CHANNEL);

        assertThat(nextDay).isEqualTo("20990202WB0000000001");
        assertThat(seqOf(sameDayAgain)).isEqualTo(3L);
    }

    @Test
    @DisplayName("10자리 상한까지 10개만 남았으면 그 10개만 나가고, 그다음은 TRF9002다")
    void reservesOnlyUpToMaxSequence() {
        repository.saveAndFlush(TransactionSequenceJpaEntity.builder()
                .seqDate(SEQ_DATE)
                .channel(CHANNEL.name())
                .lastSeq(MAX_SEQUENCE - 10)
                .updatedAt(LocalDateTime.now())
                .build());
        SequenceGenerator generator = newServer();

        List<Long> seqs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seqs.add(seqOf(generator.nextTransactionNumber(SEQ_DATE, CHANNEL)));
        }

        assertThat(seqs).first().isEqualTo(MAX_SEQUENCE - 9);
        assertThat(seqs).last().isEqualTo(MAX_SEQUENCE);
        assertThat(lastSeq(SEQ_DATE)).isEqualTo(MAX_SEQUENCE);
        assertThatThrownBy(() -> generator.nextTransactionNumber(SEQ_DATE, CHANNEL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TransferErrorCode.TRANSACTION_SEQUENCE_EXHAUSTED);
    }

    @Test
    @DisplayName("묶음 크기가 1보다 작으면 만들 때 거부한다")
    void rejectsBlockSizeBelowOne() {
        assertThatThrownBy(() -> new SequenceGenerator(repository, transactionManager, Clock.systemDefaultZone(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SequenceGenerator newServer() {
        return new SequenceGenerator(repository, transactionManager, Clock.systemDefaultZone(), BLOCK_SIZE);
    }

    private long lastSeq(LocalDate date) {
        return repository
                .findById(new TransactionSequenceId(date, CHANNEL.name()))
                .orElseThrow()
                .getLastSeq();
    }

    private static long seqOf(String transactionNumber) {
        return Long.parseLong(transactionNumber.substring(10));
    }
}
