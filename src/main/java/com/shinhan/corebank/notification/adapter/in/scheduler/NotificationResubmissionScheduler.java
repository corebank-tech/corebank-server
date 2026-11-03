package com.shinhan.corebank.notification.adapter.in.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 실패했거나 멈춘 발행 기록을 다시 보낸다(#709). 기록 대상 리스너는 알림 하나뿐이다(ADR-0010). 배치 락은 두지 않는다 —
// 처리 중인 기록은 조건부 UPDATE 가 막고, 다른 인스턴스가 한 번 더 보내도 알림은 (type, ref_id) 로 한 건이다.
@Component
public class NotificationResubmissionScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationResubmissionScheduler.class);

    // 재제출 횟수는 가져갈 때만 늘어 DB 장애 중에는 늘지 않는다. 세 번 연속 실패면 고쳐야 할 문제로 본다
    private static final int MAX_RESUBMISSIONS = 3;
    // 라이브러리는 기록을 만들 때 첫 전달을 시도 1번으로 넣는다
    private static final int FIRST_DELIVERY_ATTEMPTS = 1;
    private static final ResubmissionOptions DEFAULT_OPTIONS = ResubmissionOptions.defaults();

    private final FailedEventPublications failedEventPublications;
    private final AtomicInteger abandonedInLastBatch = new AtomicInteger();

    public NotificationResubmissionScheduler(
            FailedEventPublications failedEventPublications, MeterRegistry meterRegistry) {
        this.failedEventPublications = failedEventPublications;
        Gauge.builder("notification.publication.abandoned", abandonedInLastBatch, AtomicInteger::get)
                .description("재제출 상한을 넘겨 다시 보내지 않는 알림 발행 기록 수(마지막 재제출 묶음 기준)")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.notification.resubmission.fixed-delay}",
            initialDelayString = "${app.notification.resubmission.fixed-delay}")
    public void resubmitFailed() {
        AtomicInteger abandonedCount = new AtomicInteger();
        failedEventPublications.resubmit(
                DEFAULT_OPTIONS.withFilter(publication -> isWithinResubmissionLimit(publication, abandonedCount)));

        int abandoned = abandonedCount.get();
        abandonedInLastBatch.set(abandoned);
        boolean isBatchFullOfAbandoned = abandoned == DEFAULT_OPTIONS.getBatchSize();
        if (isBatchFullOfAbandoned) {
            log.error(
                    "읽기 묶음이 포기한 기록으로 가득 차 새 실패 기록이 밀린다 - 재제출 상한={}번, abandonedCount={}", MAX_RESUBMISSIONS, abandoned);
        } else if (abandoned > 0) {
            log.error("재제출 상한({}번)을 넘긴 알림 발행 기록 - abandonedCount={}", MAX_RESUBMISSIONS, abandoned);
        }
    }

    private static boolean isWithinResubmissionLimit(EventPublication publication, AtomicInteger abandonedCount) {
        int resubmissions = publication.getCompletionAttempts() - FIRST_DELIVERY_ATTEMPTS;
        boolean isWithinLimit = resubmissions < MAX_RESUBMISSIONS;
        if (!isWithinLimit) {
            abandonedCount.incrementAndGet();
        }
        return isWithinLimit;
    }
}
