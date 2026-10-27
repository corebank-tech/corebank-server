package com.shinhan.corebank.customer.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.pii.backfill",
        name = "execute",
        havingValue = "true")
public class CustomerPiiLocalBackfillRunner {

    private final CustomerPiiBackfillService backfillService;

    @EventListener(ApplicationReadyEvent.class)
    public void runOnce() {
        var report = backfillService.backfill(100, 10);

        log.info(
                "PH-18 local backfill completed: updated={}, batches={}, remaining={}",
                report.updatedRows(),
                report.committedBatches(),
                report.remainingRows());

        if (report.remainingRows() > 0) {
            throw new IllegalStateException("PH-18 백필되지 않은 고객 데이터가 남아 있습니다.");
        }
    }
}
