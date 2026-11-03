package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
        properties = {
            "app.pii.enabled=true",
            "app.pii.read-encrypted=false",
            "APP_PII_AES_KEY_BASE64=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "APP_PII_HMAC_KEY_BASE64=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
        })
class CustomerPiiBackfillConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final String OLD_PHONE = "01012345678";
    private static final String NEW_PHONE = "01099998888";

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private CustomerPiiBackfillService backfillService;

    @Autowired
    private CustomerPiiCrypto crypto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdCustomerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long customerId : createdCustomerIds) {
            jdbc.update("DELETE FROM customer WHERE customer_id = ?", customerId);
        }
    }

    @Test
    void backfillDoesNotOverwriteConcurrentContactUpdate() throws Exception {

        Fixture fixture = createLegacyCustomer();

        CountDownLatch writerHoldingLock = new CountDownLatch(1);
        CountDownLatch allowWriterCommit = new CountDownLatch(1);
        CountDownLatch backfillStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> writer = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);

                transaction.executeWithoutResult(status -> {
                    Customer customer = customerPersistencePort
                            .findByIdForUpdate(fixture.customerId())
                            .orElseThrow();

                    customer.changeContactInfo(NEW_PHONE, fixture.updatedEmail());

                    customerPersistencePort.updateContactInfo(customer);

                    // 행 잠금을 유지한 채 백필 스레드를 시작한다.
                    writerHoldingLock.countDown();
                    await(allowWriterCommit);
                });
            });

            assertThat(writerHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<CustomerPiiBackfillService.BackfillReport> backfill = executor.submit(() -> {
                backfillStarted.countDown();

                return backfillService.backfillRange(fixture.customerId() - 1, fixture.customerId(), 1, 1);
            });

            assertThat(backfillStarted.await(10, TimeUnit.SECONDS)).isTrue();

            try {
                // 고객정보 변경 트랜잭션이 행 잠금을 보유 중이므로
                // 백필은 곧바로 완료되지 않아야 한다.
                assertThatThrownBy(() -> backfill.get(400, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                // 연락처 변경 트랜잭션의 커밋 허용
                allowWriterCommit.countDown();
            }

            writer.get(10, TimeUnit.SECONDS);

            CustomerPiiBackfillService.BackfillReport report = backfill.get(10, TimeUnit.SECONDS);

            // 연락처 변경이 이미 암호문을 저장했으므로
            // 백필이 새 암호문을 덮어쓰지 않아야 한다.
            assertThat(report.updatedRows()).isZero();
            assertThat(report.remainingRows()).isZero();

            assertContactConsistent(fixture.customerId(), fixture.updatedEmail(), NEW_PHONE);

        } finally {
            allowWriterCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void backfillPreservesAlreadyPopulatedColumns() {
        Fixture fixture = createLegacyCustomer();

        backfillService.backfillRange(fixture.customerId() - 1, fixture.customerId(), 1, 1);

        Map<String, Object> before = readRow(fixture.customerId());

        // 전화번호 암호문만 일부러 누락시킨다.
        jdbc.update(
                """
                UPDATE customer
                SET phone_number_ciphertext = NULL
                WHERE customer_id = ?
                """,
                fixture.customerId());

        var report = backfillService.backfillRange(fixture.customerId() - 1, fixture.customerId(), 1, 1);

        Map<String, Object> after = readRow(fixture.customerId());

        assertThat(report.updatedRows()).isEqualTo(1);
        assertThat(report.remainingRows()).isZero();

        // 이미 채워진 이메일 암호문과 HMAC은 그대로여야 한다.
        assertThat(after.get("email_ciphertext")).isEqualTo(before.get("email_ciphertext"));

        assertThat(after.get("email_search_hash")).isEqualTo(before.get("email_search_hash"));

        assertContactConsistent(fixture.customerId(), fixture.originalEmail(), OLD_PHONE);
    }

    @Test
    void contactUpdateAfterBackfillRemainsConsistent() {
        Fixture fixture = createLegacyCustomer();

        backfillService.backfillRange(fixture.customerId() - 1, fixture.customerId(), 1, 1);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            Customer customer = customerPersistencePort
                    .findByIdForUpdate(fixture.customerId())
                    .orElseThrow();

            customer.changeContactInfo(NEW_PHONE, fixture.updatedEmail());

            customerPersistencePort.updateContactInfo(customer);
        });

        assertContactConsistent(fixture.customerId(), fixture.updatedEmail(), NEW_PHONE);

        Map<String, Object> before = readRow(fixture.customerId());

        // 재실행해도 연락처 변경 결과가 유지돼야 한다.
        var report = backfillService.backfillRange(fixture.customerId() - 1, fixture.customerId(), 1, 1);

        Map<String, Object> after = readRow(fixture.customerId());

        assertThat(report.updatedRows()).isZero();
        assertThat(report.remainingRows()).isZero();

        assertThat(after.get("email_ciphertext")).isEqualTo(before.get("email_ciphertext"));

        assertThat(after.get("phone_number_ciphertext")).isEqualTo(before.get("phone_number_ciphertext"));

        assertContactConsistent(fixture.customerId(), fixture.updatedEmail(), NEW_PHONE);
    }

    private Fixture createLegacyCustomer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String userId = "ph18race" + suffix;
        String originalEmail = "before-" + suffix + "@ph18race.test";
        String updatedEmail = "after-" + suffix + "@ph18race.test";

        Customer saved = customerPersistencePort.save(Customer.register(
                userId,
                null,
                PASSWORD_HASH,
                "동시성고객",
                LocalDate.of(1990, 1, 1),
                originalEmail,
                OLD_PHONE,
                LocalDateTime.of(2026, 10, 29, 10, 0)));

        Long customerId = saved.getCustomerId();
        createdCustomerIds.add(customerId);

        // 과거 서버가 평문만 저장한 고객을 재현한다.
        jdbc.update(
                """
                UPDATE customer
                SET email_ciphertext = NULL,
                    phone_number_ciphertext = NULL,
                    email_search_hash = NULL
                WHERE customer_id = ?
                """,
                customerId);

        return new Fixture(customerId, originalEmail, updatedEmail);
    }

    private void assertContactConsistent(long customerId, String expectedEmail, String expectedPhone) {

        Map<String, Object> row = readRow(customerId);

        assertThat(row.get("email")).isEqualTo(expectedEmail);
        assertThat(row.get("phone_number")).isEqualTo(expectedPhone);

        assertThat(crypto.cipher().decrypt((String) row.get("email_ciphertext"), EMAIL))
                .isEqualTo(expectedEmail);

        assertThat(crypto.cipher().decrypt((String) row.get("phone_number_ciphertext"), PHONE_NUMBER))
                .isEqualTo(expectedPhone);

        assertThat(row.get("email_search_hash"))
                .isEqualTo(crypto.emailBlindIndex().hash(expectedEmail));
    }

    private Map<String, Object> readRow(long customerId) {
        return jdbc.queryForMap(
                """
                SELECT email,
                       phone_number,
                       email_ciphertext,
                       phone_number_ciphertext,
                       email_search_hash
                FROM customer
                WHERE customer_id = ?
                """,
                customerId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    private record Fixture(Long customerId, String originalEmail, String updatedEmail) {}
}
