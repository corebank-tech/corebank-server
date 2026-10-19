package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public final class DailyLoadService implements DailyLoadUseCase {

    // 은행 앱의 배치 락(BatchExecutionLockPersistenceAdapter)과 같은 기준이다.
    private static final Duration STALE_RUNNING_AFTER = Duration.ofHours(6);

    private final ReplicaBalancePort replicaPort;
    private final MartLoadPort martLoadPort;
    private final Pseudonymizer pseudonymizer;

    public DailyLoadService(ReplicaBalancePort replicaPort, MartLoadPort martLoadPort, Pseudonymizer pseudonymizer) {
        this.replicaPort = replicaPort;
        this.martLoadPort = martLoadPort;
        this.pseudonymizer = pseudonymizer;
    }

    @Override
    public LoadResult load(LocalDate baseDate) {
        long loadBatchId = martLoadPort.start(baseDate, STALE_RUNNING_AFTER); // T1: 시작 기록은 뒤에서 실패해도 남아야 한다.
        try {
            List<BalanceRow> rows = replicaPort.readBalances(baseDate);
            if (rows.isEmpty()) {
                throw new IllegalStateException("레플리카에서 읽은 잔액이 0건입니다. 레플리카가 아직 따라오지 못했을 수 있습니다.");
            }
            return martLoadPort.publish(
                    loadBatchId, baseDate, rows.stream().map(this::toMartRow).toList()); // T2
        } catch (RuntimeException e) {
            recordFailure(loadBatchId, e); // T3
            throw e;
        }
    }

    // 개인정보를 가리는 것은 업무 규칙이라 서비스가 한다. 어댑터는 원래 ID를 받지 못한다.
    private MartBalanceRow toMartRow(BalanceRow row) {
        return new MartBalanceRow(
                pseudonymizer.accountKey(row.accountId()),
                pseudonymizer.customerKey(row.customerId()),
                row.accountType(),
                row.productId(),
                row.productGroup(),
                row.balance());
    }

    private void recordFailure(long loadBatchId, RuntimeException cause) {
        try {
            martLoadPort.fail(loadBatchId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException recordingFailure) {
            cause.addSuppressed(recordingFailure);
        }
    }
}
