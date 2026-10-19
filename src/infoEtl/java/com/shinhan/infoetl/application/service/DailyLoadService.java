package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.out.LoadLock;
import com.shinhan.infoetl.application.port.out.LoadLockPort;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.application.port.out.ReplicaEventPort;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public final class DailyLoadService implements DailyLoadUseCase {

    // 은행 앱의 배치 락(BatchExecutionLockPersistenceAdapter)과 같은 기준이다.
    private static final Duration STALE_RUNNING_AFTER = Duration.ofHours(6);

    private final ReplicaBalancePort replicaPort;
    private final ReplicaEventPort eventPort;
    private final MartLoadPort martLoadPort;
    private final LoadLockPort lockPort;
    private final Pseudonymizer pseudonymizer;

    public DailyLoadService(
            ReplicaBalancePort replicaPort,
            ReplicaEventPort eventPort,
            MartLoadPort martLoadPort,
            LoadLockPort lockPort,
            Pseudonymizer pseudonymizer) {
        this.replicaPort = replicaPort;
        this.eventPort = eventPort;
        this.martLoadPort = martLoadPort;
        this.lockPort = lockPort;
        this.pseudonymizer = pseudonymizer;
    }

    @Override
    public LoadResult load(LocalDate baseDate) {
        try (LoadLock ignored = lockPort.acquire(baseDate)) { // 같은 기준일을 두 프로세스가 동시에 적재하지 않게 한다.
            long loadBatchId = martLoadPort.start(baseDate, STALE_RUNNING_AFTER); // T1: 뒤에서 실패해도 남아야 한다.
            try {
                List<BalanceRow> balances = replicaPort.readBalances(baseDate);
                if (balances.isEmpty()) {
                    throw new IllegalStateException("레플리카에서 읽은 잔액이 0건입니다. 레플리카가 아직 따라오지 못했을 수 있습니다.");
                }
                // 가입·해지 이벤트는 그날 없을 수 있어서 0건이어도 정상이다.
                List<SubscriptionEventRow> events = eventPort.readSubscriptionEvents(baseDate);
                return martLoadPort.publish(
                        loadBatchId,
                        baseDate,
                        balances.stream().map(this::toMartBalanceRow).toList(),
                        events.stream().map(this::toMartEventRow).toList()); // T2
            } catch (RuntimeException e) {
                recordFailure(loadBatchId, e); // T3
                throw e;
            }
        }
    }

    // 개인정보를 가리는 것은 업무 규칙이라 서비스가 한다. 어댑터는 원래 ID를 받지 못한다.
    private MartBalanceRow toMartBalanceRow(BalanceRow row) {
        return new MartBalanceRow(
                pseudonymizer.accountKey(row.accountId()),
                pseudonymizer.customerKey(row.customerId()),
                row.accountType(),
                row.productId(),
                row.productGroup(),
                row.balance());
    }

    private MartEventRow toMartEventRow(SubscriptionEventRow row) {
        return new MartEventRow(
                row.eventType(),
                pseudonymizer.accountKey(row.accountId()),
                pseudonymizer.customerKey(row.customerId()),
                row.productId(),
                row.productCode(),
                row.productName(),
                row.productGroup());
    }

    private void recordFailure(long loadBatchId, RuntimeException cause) {
        try {
            martLoadPort.fail(loadBatchId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException recordingFailure) {
            cause.addSuppressed(recordingFailure);
        }
    }
}
