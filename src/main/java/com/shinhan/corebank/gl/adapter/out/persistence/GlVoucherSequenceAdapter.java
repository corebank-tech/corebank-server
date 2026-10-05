package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.application.port.out.VoucherSequencePort;
import com.shinhan.corebank.gl.domain.GlTxType;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 전표번호 채번 (PH-21). 이체 거래번호 채번기({@code transfer...SequenceGenerator})와 같은 구조다.
 *
 * <p>카운터 행을 별도 트랜잭션(REQUIRES_NEW)에서 올린다. 호출자 트랜잭션 안에서 올리면 (거래일, 유형) 행의
 * 락을 이체가 커밋될 때까지 잡고 있어 같은 날 같은 유형의 기표가 전부 직렬화된다. 대가는 호출자가
 * 롤백될 때 생기는 결번이다.
 *
 * <p>그날 카운터 행이 없으면 {@code gl_voucher} 에 이미 있는 가장 큰 번호에서 이어 간다. 시드(PH-60b)는
 * 카운터를 거치지 않고 전표를 직접 넣기 때문에, 0부터 시작하면 시드와 같은 날짜에서 번호가 겹친다.
 */
@Component
public class GlVoucherSequenceAdapter implements VoucherSequencePort {

    private static final int MAX_FIRST_INSERT_RACE_RETRIES = 5;
    private static final long BACKOFF_MIN_MILLIS = 10L;
    private static final long BACKOFF_MAX_MILLIS_PER_ATTEMPT = 30L;
    private static final int SEQUENCE_DIGITS = 6;

    private final GlVoucherSequenceJpaRepository sequenceRepository;
    private final GlVoucherJpaRepository voucherRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final Clock clock;

    public GlVoucherSequenceAdapter(
            GlVoucherSequenceJpaRepository sequenceRepository,
            GlVoucherJpaRepository voucherRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.sequenceRepository = sequenceRepository;
        this.voucherRepository = voucherRepository;
        this.clock = clock;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public VoucherNumber nextVoucherNumber(LocalDate tradeDate, GlTxType txType) {
        Objects.requireNonNull(tradeDate, "tradeDate must not be null");
        Objects.requireNonNull(txType, "txType must not be null");

        DataAccessException lastRaceFailure = null;
        for (int attempt = 0; attempt < MAX_FIRST_INSERT_RACE_RETRIES; attempt++) {
            try {
                int sequence = requiresNewTransactionTemplate.execute(status -> incrementAndGet(tradeDate, txType));
                return new VoucherNumber(tradeDate, txType, sequence);
            } catch (DataIntegrityViolationException | TransientDataAccessException raceOnFirstOfDayInsert) {
                // 같은 (거래일, 유형)의 첫 행을 두 요청이 동시에 INSERT 하면 하나가 PK 위반이나 데드락으로 진다.
                // 진 쪽은 다시 돌면 이미 생긴 행을 FOR UPDATE 로 잡는다.
                lastRaceFailure = raceOnFirstOfDayInsert;
                boolean hasNextAttempt = attempt < MAX_FIRST_INSERT_RACE_RETRIES - 1;
                if (hasNextAttempt) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        throw lastRaceFailure;
    }

    /** 동시에 진 요청들이 같은 순간에 다시 부딪치지 않도록 재시도 간격을 흩뜨린다. */
    private void sleepBeforeRetry(int attempt) {
        long delayMillis = ThreadLocalRandom.current()
                .nextLong(BACKOFF_MIN_MILLIS, BACKOFF_MAX_MILLIS_PER_ATTEMPT * (attempt + 1));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private int incrementAndGet(LocalDate tradeDate, GlTxType txType) {
        Optional<GlVoucherSequenceJpaEntity> found = sequenceRepository.findForUpdate(tradeDate, txType);
        if (found.isPresent()) {
            GlVoucherSequenceJpaEntity counter = found.get();
            if (counter.getLastSeq() >= VoucherNumber.MAX_SEQUENCE) {
                throw new BusinessException(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
            }
            return counter.incrementAndGet(LocalDateTime.now(clock));
        }

        int next = lastIssuedSequence(tradeDate, txType) + 1;
        if (next > VoucherNumber.MAX_SEQUENCE) {
            throw new BusinessException(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
        }
        sequenceRepository.saveAndFlush(
                GlVoucherSequenceJpaEntity.startAt(tradeDate, txType, next, LocalDateTime.now(clock)));
        return next;
    }

    private int lastIssuedSequence(LocalDate tradeDate, GlTxType txType) {
        String from = new VoucherNumber(tradeDate, txType, 1).value();
        String to = new VoucherNumber(tradeDate, txType, VoucherNumber.MAX_SEQUENCE).value();
        return voucherRepository
                .findMaxVoucherNoBetween(from, to)
                .map(voucherNo -> Integer.parseInt(voucherNo.substring(voucherNo.length() - SEQUENCE_DIGITS)))
                .orElse(0);
    }
}
