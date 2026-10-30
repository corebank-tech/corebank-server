package com.shinhan.corebank.customer.adapter.out.persistence;

import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import com.shinhan.piimaintenance.CustomerPiiVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local | qa-seed | phase2-seed")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.pii.backfill", name = "execute", havingValue = "true")
public class CustomerPiiSeedBackfillRunner {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 2_000;

    private final CustomerPiiBackfillService backfillService;
    private final CustomerPiiCrypto crypto;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void runOnce() {
        verifyExistingEncryptedRows();

        var report = backfillService.backfill(BATCH_SIZE, MAX_BATCHES);

        if (report.remainingRows() != 0) {
            throw new IllegalStateException("PH-18 백필 대상 고객이 남아 있습니다. 남은 건수: " + report.remainingRows());
        }

        log.info(
                "PH-18 seed backfill completed: updated={}, batches={}, remaining={}",
                report.updatedRows(),
                report.committedBatches(),
                report.remainingRows());
    }

    private void verifyExistingEncryptedRows() {
        var verifier = new CustomerPiiVerificationService(jdbcTemplate, crypto);

        var report = verifier.verifyExistingEncryptedRows();

        if (!report.existingDataConsistent()) {
            throw new IllegalStateException("기존 개인정보 암호문 검증에 실패하여 시드 백필을 중단합니다.");
        }

        log.info("PH-18 seed pre-backfill check passed: checked={}", report.checkedRows());
    }
}
