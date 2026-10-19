package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 포트 호출 하나가 연결과 트랜잭션 하나를 소유한다. 서비스는 Connection을 보지 못한다.
public final class JdbcMartLoadAdapter implements MartLoadPort {

    private static final String EVENT_UNIQUE_KEY = "uk_mpse_account_event";

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection mart) throws SQLException;
    }

    private final JdbcConnectionFactory connections;
    private final Clock clock;
    private final MartBalanceWriter balanceWriter = new MartBalanceWriter();
    private final MartEventWriter eventWriter = new MartEventWriter();
    private final LoadBatchRepository repository = new LoadBatchRepository();

    public JdbcMartLoadAdapter(JdbcConnectionFactory connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    @Override
    public long start(LocalDate baseDate, Duration staleAfter) {
        return inTransaction("마트 적재 시작 기록에 실패했습니다", mart -> {
            repository.failStaleRunning(
                    mart, now().minus(staleAfter), "비정상 종료로 간주함: " + staleAfter.toHours() + "시간 넘게 RUNNING 상태");
            return repository.start(mart, baseDate, now());
        });
    }

    @Override
    public LoadResult publish(
            long loadBatchId, LocalDate baseDate, List<MartBalanceRow> balances, List<MartEventRow> events) {
        return inTransaction("마트 적재에 실패했습니다", mart -> {
            balanceWriter.deleteByBaseDate(mart, baseDate);
            eventWriter.deleteByBaseDate(mart, baseDate);
            balanceWriter.insert(mart, loadBatchId, baseDate, balances);
            insertEvents(mart, loadBatchId, baseDate, events);
            long balanceCount = balanceWriter.countRows(mart, baseDate);
            long eventCount = eventWriter.countRows(mart, baseDate);
            long balanceTotal = balanceWriter.sumBalance(mart, baseDate);
            repository.replacePrevious(mart, baseDate, loadBatchId);
            repository.markCompleted(mart, loadBatchId, now(), balanceCount, eventCount, balanceTotal);
            return new LoadResult(loadBatchId, baseDate, balanceCount, eventCount, balanceTotal);
        });
    }

    @Override
    public void fail(long loadBatchId, String reason) {
        inTransaction("마트 적재 실패 기록에 실패했습니다", mart -> {
            repository.markFailed(mart, loadBatchId, reason);
            return null;
        });
    }

    // 같은 계좌의 이벤트가 이미 다른 기준일에 있으면(예: 해지 계좌의 마지막 원장 거래일이 옮겨진 경우) 안내를 남긴다.
    private void insertEvents(Connection mart, long loadBatchId, LocalDate baseDate, List<MartEventRow> events)
            throws SQLException {
        try {
            eventWriter.insert(mart, loadBatchId, baseDate, events);
        } catch (SQLException e) {
            if (mentionsEventUniqueKey(e)) {
                throw new EtlStorageException("같은 계좌의 이벤트가 다른 기준일에 이미 있습니다. 그 기준일을 먼저 재적재하세요: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private static boolean mentionsEventUniqueKey(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(EVENT_UNIQUE_KEY)) {
                return true;
            }
        }
        return false;
    }

    private <T> T inTransaction(String failureMessage, Work<T> work) {
        try (Connection mart = connections.open()) {
            mart.setAutoCommit(false);
            try {
                T result = work.run(mart);
                mart.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(mart, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new EtlStorageException(failureMessage + ": " + e.getMessage(), e);
        }
    }

    private static void rollback(Connection mart, Exception cause) {
        try {
            mart.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
