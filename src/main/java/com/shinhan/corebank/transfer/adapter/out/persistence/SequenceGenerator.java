package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * transaction_sequence 테이블 기반 거래번호 채번기.
 * 일자·채널별 행에 PESSIMISTIC_WRITE 락을 걸어 증가시키되, 당일 최초 채번(행 없음)
 * 시점에는 락 대상 자체가 없어 동시 INSERT가 경합할 수 있다. 이를 REQUIRES_NEW
 * 독립 트랜잭션으로 감싸 락 보유 시간을 최소화하고, INSERT 충돌 시(PK 유일 제약
 * 위반 또는 InnoDB가 동시 INSERT를 데드락으로 해소하는 경우 모두 관측됨) 이미 다른
 * 스레드가 만든 행을 재조회해 재시도한다.
 *
 * 행을 잠글 때마다 번호를 blockSize개씩 예약해 두고 메모리에서 하나씩 나눠 준다(#786). 모든 웹 이체가
 * 같은 행 하나에 줄을 서므로, 잠그는 횟수를 줄여야 처리 상한이 오른다. 같은 서버 안의 스레드는 묶음
 * 잠금으로, 서버끼리는 행 락으로 줄을 선다. 재시작하면 쓰지 않은 번호는 버려져 빈칸이 생긴다.
 */
@Component
public class SequenceGenerator implements TransferSequencePort {

    private static final int SEQUENCE_DIGITS = 10;
    private static final long INITIAL_SEQUENCE = 1L;
    private static final long MAX_SEQUENCE = 9_999_999_999L;
    private static final int MAX_FIRST_INSERT_RACE_RETRIES = 5;
    private static final long BACKOFF_MIN_MILLIS = 10L;
    private static final long BACKOFF_MAX_MILLIS_PER_ATTEMPT = 30L;

    private final TransactionSequenceJpaRepository repository;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final Clock clock;
    private final int blockSize;
    private final ConcurrentMap<BlockKey, Block> blocks = new ConcurrentHashMap<>();

    public SequenceGenerator(
            TransactionSequenceJpaRepository repository,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${app.transaction-sequence.block-size}") int blockSize) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("blockSize must be at least 1: " + blockSize);
        }
        this.repository = repository;
        this.clock = clock;
        this.blockSize = blockSize;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String nextTransactionNumber(LocalDate seqDate, TransferChannel channel) {
        Objects.requireNonNull(seqDate, "seqDate must not be null");
        Objects.requireNonNull(channel, "channel must not be null");

        long nextSeq = nextSeq(seqDate, channel.name());
        return format(seqDate, channel, nextSeq);
    }

    private long nextSeq(LocalDate seqDate, String channel) {
        Block block = blocks.computeIfAbsent(new BlockKey(seqDate, channel), key -> new Block());
        // 묶음을 다 쓴 스레드가 예약하는 동안 같은 일자·채널의 다른 스레드는 여기서 기다린다
        block.lock.lock();
        try {
            if (block.next > block.end) {
                SequenceRange range = reserveBlock(seqDate, channel);
                block.next = range.start();
                block.end = range.end();
            }
            return block.next++;
        } finally {
            block.lock.unlock();
        }
    }

    private SequenceRange reserveBlock(LocalDate seqDate, String channel) {
        DataAccessException lastRaceFailure = null;

        for (int attempt = 0; attempt < MAX_FIRST_INSERT_RACE_RETRIES; attempt++) {
            try {
                return requiresNewTransactionTemplate.execute(status -> doReserve(seqDate, channel));
            } catch (DataIntegrityViolationException | TransientDataAccessException raceOnFirstOfDayInsert) {
                // 재시도 대상: (seq_date, channel) PK 유일 제약 위반, 또는 InnoDB가 동시 INSERT를
                // 데드락/락대기타임아웃으로 해소하는 경우(TransientDataAccessException 하위 타입).
                // 그 외 DataAccessException(SQL 문법, 권한, 연결 장애 등)은 재시도로 해결되지
                // 않으므로 즉시 전파한다.
                lastRaceFailure = raceOnFirstOfDayInsert;
                boolean hasNextAttempt = attempt < MAX_FIRST_INSERT_RACE_RETRIES - 1;
                if (hasNextAttempt) {
                    sleepBeforeRetry(attempt);
                }
            }
        }

        throw lastRaceFailure;
    }

    /**
     * 재시도 사이에 짧은 jitter를 둬서, 동시에 실패한 스레드들이 같은 타이밍에
     * 다시 충돌하는 것을 완화한다. attempt가 커질수록 지연 상한도 함께 늘어난다.
     */
    private void sleepBeforeRetry(int attempt) {
        long delayMillis = ThreadLocalRandom.current()
                .nextLong(BACKOFF_MIN_MILLIS, BACKOFF_MAX_MILLIS_PER_ATTEMPT * (attempt + 1));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private SequenceRange doReserve(LocalDate seqDate, String channel) {
        Optional<TransactionSequenceJpaEntity> found = repository.findBySeqDateAndChannelForUpdate(seqDate, channel);

        if (found.isPresent()) {
            TransactionSequenceJpaEntity entity = found.get();
            if (entity.getLastSeq() >= MAX_SEQUENCE) {
                throw new BusinessException(TransferErrorCode.TRANSACTION_SEQUENCE_EXHAUSTED);
            }
            long start = entity.getLastSeq() + 1;
            return new SequenceRange(start, entity.reserve(blockSize, MAX_SEQUENCE, LocalDateTime.now(clock)));
        }

        long end = Math.min(blockSize, MAX_SEQUENCE);
        repository.saveAndFlush(TransactionSequenceJpaEntity.builder()
                .seqDate(seqDate)
                .channel(channel)
                .lastSeq(end)
                .updatedAt(LocalDateTime.now(clock))
                .build());
        return new SequenceRange(INITIAL_SEQUENCE, end);
    }

    private String format(LocalDate seqDate, TransferChannel channel, long seq) {
        return seqDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + channel.name()
                + String.format("%0" + SEQUENCE_DIGITS + "d", seq);
    }

    private record BlockKey(LocalDate seqDate, String channel) {}

    private record SequenceRange(long start, long end) {}

    // next > end 이면 비어 있다. 두 값은 lock을 쥔 스레드만 읽고 쓴다
    private static final class Block {
        private final ReentrantLock lock = new ReentrantLock();
        private long next = 1;
        private long end = 0;
    }
}
