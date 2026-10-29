package com.shinhan.piimaintenance;

import com.shinhan.corebank.customer.adapter.out.persistence.CustomerPiiBackfillService;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

@Slf4j
@RequiredArgsConstructor
public class CustomerPiiMaintenanceRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 10_000;

    private final CustomerPiiBackfillService backfillService;
    private final CustomerPiiVerificationService verifier;
    private final DataSource dataSource;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        String action = environment.getProperty("app.pii.maintenance.action", "");

        if (!action.equals("backfill") && !action.equals("verify")) {
            throw new IllegalStateException("PII 작업 종류는 backfill 또는 verify여야 합니다.");
        }

        if (!environment.getProperty("app.pii.enabled", Boolean.class, false)) {
            throw new IllegalStateException("PII 작업에는 app.pii.enabled=true가 필요합니다.");
        }

        if (environment.getProperty("app.pii.read-encrypted", Boolean.class, false)) {
            throw new IllegalStateException("PII 작업 중에는 app.pii.read-encrypted=false를 사용하세요.");
        }

        verifyDatabaseTarget();

        if (action.equals("backfill")) {
            runBackfill();
            return;
        }

        runVerification();
    }

    private void runBackfill() {
        // 잘못된 키로 기존 암호문과 다른 키의 암호문을
        // 혼합 생성하지 않도록 백필 전에 검사한다.
        var precheck = verifier.verifyExistingEncryptedRows();

        if (!precheck.existingDataConsistent()) {
            throw new IllegalStateException("기존 개인정보 암호문 검증에 실패하여 백필을 중단합니다.");
        }

        log.info("PH-18 pre-backfill key check passed: checked={}", precheck.checkedRows());

        var report = backfillService.backfill(BATCH_SIZE, MAX_BATCHES);

        log.info(
                "PH-18 maintenance backfill: updated={}, batches={}, remaining={}",
                report.updatedRows(),
                report.committedBatches(),
                report.remainingRows());

        if (report.remainingRows() != 0) {
            throw new IllegalStateException("PII 백필 미완료: 남은 행 수=" + report.remainingRows());
        }
    }

    private void runVerification() {
        var report = verifier.verifyAll();

        log.info(
                "PH-18 maintenance verification: "
                        + "checked={}, missing={}, emailMismatch={}, "
                        + "phoneMismatch={}, hashMismatch={}, cryptoFailure={}",
                report.checkedRows(),
                report.missingRows(),
                report.emailMismatchRows(),
                report.phoneMismatchRows(),
                report.hashMismatchRows(),
                report.cryptoFailureRows());

        if (!report.passed()) {
            throw new IllegalStateException("전체 고객 PII 검증에 실패했습니다. " + "암호화 조회 전환을 금지합니다.");
        }
    }

    private void verifyDatabaseTarget() throws SQLException {
        String expectedHost = required("app.pii.maintenance.expected-db-host");

        String expectedDb = required("app.pii.maintenance.expected-db-name");

        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();

            String actualHost = URI.create(jdbcUrl.substring("jdbc:".length())).getHost();

            String actualDb = connection.getCatalog();

            if (actualHost == null
                    || !actualHost.equalsIgnoreCase(expectedHost)
                    || actualDb == null
                    || !actualDb.equalsIgnoreCase(expectedDb)) {

                throw new IllegalStateException("PII 작업 대상 DB가 승인된 접속 대상과 " + "일치하지 않습니다.");
            }

            log.info(
                    "PH-18 maintenance target confirmed: database={}, host={}",
                    actualDb,
                    actualHost.toLowerCase(Locale.ROOT));
        }
    }

    private String required(String key) {
        String value = environment.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PII 작업에 필수 대상 설정이 없습니다: " + key);
        }

        return value.trim();
    }
}
