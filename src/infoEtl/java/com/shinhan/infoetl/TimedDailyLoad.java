package com.shinhan.infoetl;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.domain.LoadResult;
import java.time.Duration;
import java.time.LocalDate;

// 단일·기간 적재 모두 하루 적재마다 소요 시간을 같은 로그로 남긴다(성공과 실패 모두).
final class TimedDailyLoad implements DailyLoadUseCase {

    private static final System.Logger LOG = System.getLogger(TimedDailyLoad.class.getName());

    private final DailyLoadUseCase delegate;

    TimedDailyLoad(DailyLoadUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    public LoadResult load(LocalDate baseDate, boolean rebuild) {
        long startedAt = System.nanoTime();
        String outcome = "실패";
        try {
            LoadResult result = delegate.load(baseDate, rebuild);
            outcome = "성공";
            return result;
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            LOG.log(
                    System.Logger.Level.INFO,
                    "적재 소요 시간 - baseDate={0}, 결과={1}, elapsed={2}ms ({3}초)",
                    baseDate,
                    outcome,
                    elapsed.toMillis(),
                    String.format("%.1f", elapsed.toMillis() / 1000.0));
        }
    }
}
