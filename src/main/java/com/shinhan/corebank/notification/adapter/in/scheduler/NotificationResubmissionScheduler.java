package com.shinhan.corebank.notification.adapter.in.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 실패했거나 멈춘 알림·FDS 매핑 발행 기록을 함께 다시 보낸다(#709, ADR-0010). 배치 락은 두지 않는다 —
// 처리 중인 기록은 조건부 UPDATE가 막고, 구독자는 업무 키로 중복 처리를 방지한다.
@Component
public class NotificationResubmissionScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationResubmissionScheduler.class);

    // 재제출 횟수는 가져갈 때만 늘어 DB 장애 중에는 늘지 않는다. 세 번 연속 실패면 고쳐야 할 문제로 본다
    private static final int MAX_RESUBMISSIONS = 3;
    // 라이브러리는 기록을 만들 때 첫 전달을 시도 1번으로 넣는다
    private static final int FIRST_DELIVERY_ATTEMPTS = 1;
    private static final ResubmissionOptions DEFAULT_OPTIONS = ResubmissionOptions.defaults();
    // 한 번 전달의 최악 처리(시도 4번 × 연결 대기 3초 + 재시도 대기 ≈ 14초)의 6배 이상 · 재제출 주기(5분) 이하 — SQS 가시성 타임아웃 권장 기준
    private static final Duration MIN_RESUBMISSION_INTERVAL = Duration.ofMinutes(2);

    private final FailedEventPublications failedEventPublications;
    private final Clock clock;
    private final AtomicInteger abandonedInLastBatch = new AtomicInteger();

    public NotificationResubmissionScheduler(
            FailedEventPublications failedEventPublications, MeterRegistry meterRegistry, Clock clock) {
        this.failedEventPublications = failedEventPublications;
        this.clock = clock;
        Gauge.builder("notification.publication.abandoned", abandonedInLastBatch, AtomicInteger::get)
                .description("재제출 상한을 넘겨 다시 보내지 않는 이벤트 발행 기록 수(마지막 재제출 묶음 기준)")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.notification.resubmission.fixed-delay}",
            initialDelayString = "${app.notification.resubmission.fixed-delay}")
    public void resubmitFailed() {
        final List<String> abandonedPublications = new ArrayList<>();
        final Instant lastPublishedBefore = clock.instant().minus(MIN_RESUBMISSION_INTERVAL);
        failedEventPublications.resubmit(
                DEFAULT_OPTIONS.withFilter(publication -> isWithinResubmissionLimit(publication, abandonedPublications)
                        && isIdleSince(publication, lastPublishedBefore)));

        final int abandoned = abandonedPublications.size();
        abandonedInLastBatch.set(abandoned);
        final boolean isBatchFullOfAbandoned = abandoned == DEFAULT_OPTIONS.getBatchSize();
        if (isBatchFullOfAbandoned) {
            log.error(
                    "포기한 기록이 재제출 대상 {}건을 모두 차지해 그 뒤 실패 기록이 재제출되지 않는다 - 런북으로 처리 필요, 재제출 상한={}번, abandoned={}",
                    abandoned,
                    MAX_RESUBMISSIONS,
                    abandonedPublications);
        } else if (abandoned > 0) {
            log.error(
                    "재제출 상한({}번)을 모두 쓴 이벤트 발행 기록 - abandonedCount={}, abandoned={}",
                    MAX_RESUBMISSIONS,
                    abandoned,
                    abandonedPublications);
        }
    }

    // 포기한 기록은 이벤트 종류와 발행 ID 로 남긴다 — 런북이 이 ID 로 대상을 집는다. 이벤트 본문은 고객 정보라 남기지 않는다
    private static boolean isWithinResubmissionLimit(EventPublication publication, List<String> abandonedPublications) {
        final int resubmissions = publication.getCompletionAttempts() - FIRST_DELIVERY_ATTEMPTS;
        final boolean isWithinLimit = resubmissions < MAX_RESUBMISSIONS;
        if (!isWithinLimit) {
            abandonedPublications.add(
                    publication.getEvent().getClass().getSimpleName() + "#" + publication.getIdentifier());
        }
        return isWithinLimit;
    }

    // 즉시 재시도 중이거나 다른 인스턴스가 방금 재제출한 기록은 FAILED 여도 건너뛴다
    private static boolean isIdleSince(EventPublication publication, Instant lastPublishedBefore) {
        return publication.getLastPublicationDate().isBefore(lastPublishedBefore);
    }
}
