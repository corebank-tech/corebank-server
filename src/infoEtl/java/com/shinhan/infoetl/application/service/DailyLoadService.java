package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import com.shinhan.infoetl.domain.AsOfWatermark;
import com.shinhan.infoetl.domain.BalanceRow;
import com.shinhan.infoetl.domain.BusinessCalendar;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.Pseudonymizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class DailyLoadService implements DailyLoadUseCase {

    // 은행 앱의 배치 락(BatchExecutionLockPersistenceAdapter)과 같은 기준이다.
    private static final Duration STALE_RUNNING_AFTER = Duration.ofHours(6);

    private final ReplicaBalancePort replicaPort;
    private final ReplicaCalendarPort calendarPort;
    private final MartLoadPort martLoadPort;
    private final Pseudonymizer pseudonymizer;

    public DailyLoadService(
            ReplicaBalancePort replicaPort,
            ReplicaCalendarPort calendarPort,
            MartLoadPort martLoadPort,
            Pseudonymizer pseudonymizer) {
        this.replicaPort = replicaPort;
        this.calendarPort = calendarPort;
        this.martLoadPort = martLoadPort;
        this.pseudonymizer = pseudonymizer;
    }

    @Override
    public LoadResult load(LocalDate baseDate) {
        requireBusinessDay(baseDate); // 잔액 행은 영업일에만 있다. 휴일은 적재 기록도 남기지 않고 거부한다.
        long loadBatchId = martLoadPort.start(baseDate, STALE_RUNNING_AFTER); // T1: 시작 기록은 뒤에서 실패해도 남아야 한다.
        try {
            List<BalanceRow> rows = replicaPort.readBalances(baseDate);
            if (rows.isEmpty()) {
                throw new IllegalStateException("레플리카에서 읽은 잔액이 0건입니다. 레플리카가 아직 따라오지 못했을 수 있습니다.");
            }
            return martLoadPort.publish(
                    loadBatchId, baseDate, rows.stream().map(this::toMartRow).toList(), this::asOf); // T2
        } catch (RuntimeException e) {
            recordFailure(loadBatchId, e); // T3
            throw e;
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
