package com.shinhan.infoetl;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DailyLoadService {

    // 은행 앱의 배치 락(BatchExecutionLockPersistenceAdapter)과 같은 기준이다.
    private static final Duration STALE_RUNNING_AFTER = Duration.ofHours(6);

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory replicaFactory;
    private final ConnectionFactory martFactory;
    private final BalanceReader reader;
    private final MartBalanceWriter writer;
    private final LoadBatchRepository repository;
    private final Pseudonymizer pseudonymizer;
    private final Clock clock;

    public DailyLoadService(
            ConnectionFactory replicaFactory,
            ConnectionFactory martFactory,
            BalanceReader reader,
            MartBalanceWriter writer,
            LoadBatchRepository repository,
            Pseudonymizer pseudonymizer,
            Clock clock) {
        this.replicaFactory = replicaFactory;
        this.martFactory = martFactory;
        this.reader = reader;
        this.writer = writer;
        this.repository = repository;
        this.pseudonymizer = pseudonymizer;
        this.clock = clock;
    }

    public LoadResult load(LocalDate baseDate) throws SQLException {
        try (Connection mart = martFactory.open()) {
            mart.setAutoCommit(false);
            repository.failStaleRunning(mart, now().minus(STALE_RUNNING_AFTER), "비정상 종료로 간주함: 6시간 넘게 RUNNING 상태");
            long loadBatchId = repository.start(mart, baseDate, now());
            mart.commit(); // T1: 시작 기록은 뒤에서 실패해도 남아야 한다.
            try {
                List<BalanceRow> rows = readFromReplica(baseDate);
                if (rows.isEmpty()) {
                    throw new IllegalStateException("레플리카에서 읽은 잔액이 0건입니다. 레플리카가 아직 따라오지 못했을 수 있습니다.");
                }
                writer.deleteByBaseDate(mart, baseDate); // T2 시작
                writer.insert(mart, loadBatchId, baseDate, rows, pseudonymizer);
                long rowCount = writer.countRows(mart, baseDate);
                long balanceTotal = writer.sumBalance(mart, baseDate);
                repository.replacePrevious(mart, baseDate, loadBatchId);
                repository.markCompleted(mart, loadBatchId, now(), rowCount, balanceTotal);
                mart.commit(); // T2: 삭제·삽입·완료 표시가 한 번에 반영된다.
                return new LoadResult(loadBatchId, baseDate, rowCount, balanceTotal);
            } catch (SQLException | RuntimeException e) {
                recordFailure(mart, loadBatchId, e); // T3
                throw e;
            }
        }
    }

    private List<BalanceRow> readFromReplica(LocalDate baseDate) throws SQLException {
        try (Connection replica = replicaFactory.open()) {
            replica.setReadOnly(true);
            return reader.read(replica, baseDate);
        }
    }

    private void recordFailure(Connection mart, long loadBatchId, Exception cause) {
        try {
            mart.rollback();
            repository.markFailed(mart, loadBatchId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
            mart.commit();
        } catch (SQLException recordingFailure) {
            cause.addSuppressed(recordingFailure);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
