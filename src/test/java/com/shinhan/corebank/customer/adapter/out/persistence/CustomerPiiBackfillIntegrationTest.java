package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(
        properties = {
            "app.pii.enabled=true",
            "APP_PII_AES_KEY_BASE64=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "APP_PII_HMAC_KEY_BASE64=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
        })
class CustomerPiiBackfillIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private CustomerPiiBackfillService backfillService;

    @Autowired
    private CustomerPiiCrypto crypto;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void backfillExistingCustomerAndSkipOnRerun() {
        long customerId = createCustomer("ph18-backfill-1", "old@example.com", "01012345678");

        // 예전 버전에서 저장한 고객 데이터 상태를 재현한다.
        jdbcTemplate.update(
                """
                UPDATE customer
                SET email_ciphertext = NULL,
                    phone_number_ciphertext = NULL,
                    email_search_hash = NULL
                WHERE customer_id = ?
                """,
                customerId);

        var first = backfillService.backfill(1, 10);

        assertThat(first.updatedRows()).isGreaterThanOrEqualTo(1);
        assertEncryptedColumns(customerId, "old@example.com", "01012345678");

        Map<String, Object> beforeRerun = readColumns(customerId);

        var second = backfillService.backfill(1, 10);

        assertThat(second.updatedRows()).isZero();
        assertThat(readColumns(customerId).get("email_ciphertext")).isEqualTo(beforeRerun.get("email_ciphertext"));
        assertThat(readColumns(customerId).get("phone_number_ciphertext"))
                .isEqualTo(beforeRerun.get("phone_number_ciphertext"));
    }

    @Test
    void backfillRepairsPartiallyMissingColumns() {
        long customerId = createCustomer("ph18-backfill-2", "partial@example.com", "01011112222");

        // 휴대폰 번호가 변경됐지만 암호문이 누락된 상태를 재현한다.
        jdbcTemplate.update(
                """
                UPDATE customer
                SET phone_number = ?,
                    phone_number_ciphertext = NULL
                WHERE customer_id = ?
                """,
                "01099998888",
                customerId);

        backfillService.backfill(2, 10);

        assertEncryptedColumns(customerId, "partial@example.com", "01099998888");
    }

    private long createCustomer(String userId, String email, String phoneNumber) {

        Customer saved = customerPersistencePort.save(Customer.register(
                userId,
                null,
                PASSWORD_HASH,
                "홍길동",
                LocalDate.of(1990, 1, 1),
                email,
                phoneNumber,
                LocalDateTime.of(2026, 10, 27, 10, 0)));

        entityManager.flush();
        entityManager.clear();

        return saved.getCustomerId();
    }

    private void assertEncryptedColumns(long customerId, String email, String phoneNumber) {

        Map<String, Object> row = readColumns(customerId);

        assertThat(row.get("email")).isEqualTo(email);
        assertThat(row.get("phone_number")).isEqualTo(phoneNumber);

        assertThat(crypto.cipher().decrypt((String) row.get("email_ciphertext"), EMAIL))
                .isEqualTo(email);

        assertThat(crypto.cipher().decrypt((String) row.get("phone_number_ciphertext"), PHONE_NUMBER))
                .isEqualTo(phoneNumber);

        assertThat(row.get("email_search_hash"))
                .isEqualTo(crypto.emailBlindIndex().hash(email));
    }

    private Map<String, Object> readColumns(long customerId) {
        return jdbcTemplate.queryForMap(
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
}
