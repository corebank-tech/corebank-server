package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.out.LoadLock;
import com.shinhan.infoetl.application.port.out.LoadLockPort;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import com.shinhan.infoetl.application.port.out.ReplicaEventPort;
import com.shinhan.infoetl.domain.AsOfWatermark;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.BusinessCalendar;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class DailyLoadService implements DailyLoadUseCase {

    // 은행 앱의 배치 락(BatchExecutionLockPersistenceAdapter)과 같은 기준이다.
    private static final Duration STALE_RUNNING_AFTER = Duration.ofHours(6);

    private final ReplicaBalancePort replicaPort;
    private final ReplicaEventPort eventPort;
    private final ReplicaCalendarPort calendarPort;
    private final CutoffPolicy cutoffPolicy;
    private final MartLoadPort martLoadPort;
    private final LoadLockPort lockPort;
    private final Pseudonymizer pseudonymizer;

    public DailyLoadService(
            ReplicaBalancePort replicaPort,
            ReplicaEventPort eventPort,
            ReplicaCalendarPort calendarPort,
            CutoffPolicy cutoffPolicy,
            MartLoadPort martLoadPort,
            LoadLockPort lockPort,
            Pseudonymizer pseudonymizer) {
        this.replicaPort = replicaPort;
        this.eventPort = eventPort;
        this.calendarPort = calendarPort;
        this.cutoffPolicy = cutoffPolicy;
        this.martLoadPort = martLoadPort;
        this.lockPort = lockPort;
        this.pseudonymizer = pseudonymizer;
    }

    @Override
    public LoadResult load(LocalDate baseDate, boolean rebuild) {
        requireBusinessDay(baseDate); // 잔액 행은 영업일에만 있다. 휴일은 락도 적재 기록도 없이 거부한다.
        cutoffPolicy.requireStartable(baseDate); // 경계일 이후인데 cutoff가 없으면 락도 적재 기록도 없이 거부한다.
        try (LoadLock ignored = lockPort.acquire(baseDate)) { // 같은 기준일을 두 프로세스가 동시에 적재하지 않게 한다.
            if (!rebuild && martLoadPort.isCompleted(baseDate)) {
                throw new IllegalArgumentException("이미 적재가 끝난 기준일입니다. 다시 만들려면 --rebuild를 함께 주세요: " + baseDate);
            }
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
                        events.stream().map(this::toMartEventRow).toList(),
                        this::asOf); // T2
            } catch (RuntimeException e) {
                recordFailure(loadBatchId, e); // T3
                throw e;
            }
        }
    }

    private void requireBusinessDay(LocalDate baseDate) {
        if (!new BusinessCalendar(calendarPort.readHolidays(baseDate, baseDate)).isBusinessDay(baseDate)) {
            throw new IllegalArgumentException("영업일이 아닌 기준일은 적재할 수 없습니다: " + baseDate);
        }
    }

    // T2 안에서 불린다. 첫 적재일부터 완료된 마지막 날짜까지의 휴일만 읽어 연속성을 판정한다.
    private LocalDate asOf(LocalDate firstLoadDate, Set<LocalDate> completedBaseDates) {
        BusinessCalendar calendar =
                new BusinessCalendar(calendarPort.readHolidays(firstLoadDate, Collections.max(completedBaseDates)));
        return AsOfWatermark.compute(firstLoadDate, completedBaseDates, calendar);
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
