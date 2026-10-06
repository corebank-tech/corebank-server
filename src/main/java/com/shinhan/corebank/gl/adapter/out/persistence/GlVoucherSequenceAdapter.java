package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.application.port.out.VoucherSequencePort;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 전표번호 채번 (PH-21). 이체 거래번호 채번기({@code transfer...SequenceGenerator})와 같은 구조다.
 *
 * <p>카운터 행을 기표 트랜잭션과 별도로 커밋한다. 기표 트랜잭션 안에서 올리면 (거래일, 유형) 행의 락을 이체가 커밋될
 * 때까지 잡고 있어 같은 날 같은 유형의 기표가 전부 직렬화된다. 대가는 기표가 롤백될 때 생기는 결번이다.
 *
 * <p><b>채번 전용 커넥션 풀을 쓴다(PH-24).</b> 채번은 이체 기표 트랜잭션 안(훅 B)에서 불린다. 메인 풀에서 커넥션을 하나
 * 더 꺼내면, 동시에 기표 중인 이체 수가 풀 크기에 닿는 순간 모두가 첫 커넥션을 쥔 채 두 번째를 기다리며 멈춘다.
 * 채번 풀은 짧게 쓰고 바로 돌려주며 메인 풀을 기다리지 않으므로 이 순환 대기가 생기지 않는다.
 *
 * <p>그날 카운터 행이 없으면 {@code gl_voucher} 에 이미 있는 가장 큰 번호에서 이어 간다. 시드(PH-60b)는 카운터를 거치지
 * 않고 전표를 직접 넣기 때문에, 0부터 시작하면 시드와 같은 날짜에서 번호가 겹친다.
 */
@Component
public class GlVoucherSequenceAdapter implements VoucherSequencePort {

    private static final int POOL_SIZE = 3;
    private static final int MAX_FIRST_INSERT_RACE_RETRIES = 5;
    private static final long BACKOFF_MIN_MILLIS = 10L;
    private static final long BACKOFF_MAX_MILLIS_PER_ATTEMPT = 30L;
    private static final int SEQUENCE_DIGITS = 6;

    private final HikariDataSource sequenceDataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public GlVoucherSequenceAdapter(JdbcConnectionDetails connectionDetails, Clock clock) {
        this.sequenceDataSource = newSequencePool(connectionDetails);
        this.jdbcTemplate = new JdbcTemplate(sequenceDataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(sequenceDataSource));
        this.clock = clock;
    }

    private static HikariDataSource newSequencePool(JdbcConnectionDetails connectionDetails) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("gl-voucher-sequence");
        dataSource.setJdbcUrl(connectionDetails.getJdbcUrl());
        dataSource.setUsername(connectionDetails.getUsername());
        dataSource.setPassword(connectionDetails.getPassword());
        dataSource.setDriverClassName(connectionDetails.getDriverClassName());
        dataSource.setMaximumPoolSize(POOL_SIZE);
        return dataSource;
    }

    @PreDestroy
    void closePool() {
        sequenceDataSource.close();
    }

    @Override
    public VoucherNumber nextVoucherNumber(LocalDate tradeDate, GlTxType txType) {
        Objects.requireNonNull(tradeDate, "tradeDate must not be null");
        Objects.requireNonNull(txType, "txType must not be null");

        DataAccessException lastRaceFailure = null;
        for (int attempt = 0; attempt < MAX_FIRST_INSERT_RACE_RETRIES; attempt++) {
            try {
                int sequence = transactionTemplate.execute(status -> incrementAndGet(tradeDate, txType));
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
        List<Integer> found = jdbcTemplate.queryForList(
                "SELECT last_seq FROM gl_voucher_sequence WHERE trade_date = ? AND tx_type = ? FOR UPDATE",
                Integer.class,
                tradeDate,
                txType.name());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!found.isEmpty()) {
            int next = found.getFirst() + 1;
            if (next > VoucherNumber.MAX_SEQUENCE) {
                throw new BusinessException(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
            }
            jdbcTemplate.update(
                    "UPDATE gl_voucher_sequence SET last_seq = ?, updated_at = ? WHERE trade_date = ? AND tx_type = ?",
                    next,
                    now,
                    tradeDate,
                    txType.name());
            return next;
        }

        int next = lastIssuedSequence(tradeDate, txType) + 1;
        if (next > VoucherNumber.MAX_SEQUENCE) {
            throw new BusinessException(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
        }
        jdbcTemplate.update(
                "INSERT INTO gl_voucher_sequence (trade_date, tx_type, last_seq, updated_at) VALUES (?, ?, ?, ?)",
                tradeDate,
                txType.name(),
                next,
                now);
        return next;
    }

    // 같은 (거래일, 유형)의 번호는 앞 13자가 같으므로 PK 범위 스캔으로 끝난다.
    private int lastIssuedSequence(LocalDate tradeDate, GlTxType txType) {
        String from = new VoucherNumber(tradeDate, txType, 1).value();
        String to = new VoucherNumber(tradeDate, txType, VoucherNumber.MAX_SEQUENCE).value();
        String lastIssued = jdbcTemplate.queryForObject(
                "SELECT MAX(voucher_no) FROM gl_voucher WHERE voucher_no BETWEEN ? AND ?", String.class, from, to);
        return lastIssued == null ? 0 : Integer.parseInt(lastIssued.substring(lastIssued.length() - SEQUENCE_DIGITS));
    }
}
