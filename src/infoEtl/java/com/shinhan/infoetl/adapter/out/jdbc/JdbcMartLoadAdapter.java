package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.AsOfCalculator;
import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 포트 호출 하나가 연결과 트랜잭션 하나를 소유한다. 서비스는 Connection을 보지 못한다.
public final class JdbcMartLoadAdapter implements MartLoadPort {

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection mart) throws SQLException;
    }

    private final JdbcConnectionFactory connections;
    private final Clock clock;
    private final MartBalanceWriter writer = new MartBalanceWriter();
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
            long loadBatchId, LocalDate baseDate, List<MartBalanceRow> rows, AsOfCalculator asOfCalculator) {
        return inTransaction("마트 적재에 실패했습니다", mart -> {
            writer.deleteByBaseDate(mart, baseDate);
            writer.insert(mart, loadBatchId, baseDate, rows);
            long rowCount = writer.countRows(mart, baseDate);
            long balanceTotal = writer.sumBalance(mart, baseDate);
            repository.replacePrevious(mart, baseDate, loadBatchId);
            Set<LocalDate> completed = new HashSet<>(repository.completedBaseDates(mart));
            completed.add(baseDate); // 이번 배치는 아직 RUNNING이라 조회에 안 잡힌다.
            LocalDate asOf = asOfCalculator.calculate(completed);
            repository.markCompleted(mart, loadBatchId, now(), rowCount, balanceTotal, asOf);
            return new LoadResult(loadBatchId, baseDate, rowCount, balanceTotal, asOf);
        });
    }

    @Override
    public void fail(long loadBatchId, String reason) {
        inTransaction("마트 적재 실패 기록에 실패했습니다", mart -> {
            repository.markFailed(mart, loadBatchId, reason);
            return null;
        });
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
