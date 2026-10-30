package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(
        properties = {
            "app.pii.enabled=true",
            "app.pii.read-encrypted=true",
            "APP_PII_AES_KEY_BASE64=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "APP_PII_HMAC_KEY_BASE64=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
        })
class CustomerPiiSeedCompatibilityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerPiiBackfillService backfillService;

    @Autowired
    private CustomerPiiCrypto crypto;

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Test
    void rawJdbcSeedIsReadableAfterBackfill() {
        long customerId = insertRawCustomer();

        // JPA를 우회했으므로 최초에는 암호문이 없다.
        Map<String, Object> before = readColumns(customerId);

        assertThat(before.get("email_ciphertext")).isNull();
        assertThat(before.get("phone_number_ciphertext")).isNull();
        assertThat(before.get("email_search_hash")).isNull();

        CustomerPiiSeedBackfillRunner runner = runner();
        runner.runOnce();

        Map<String, Object> after = readColumns(customerId);

        assertThat(after.get("email_ciphertext")).isNotNull();
        assertThat(after.get("phone_number_ciphertext")).isNotNull();
        assertThat(after.get("email_search_hash")).isNotNull();

        // DB 평문을 직접 사용하지 않고 암호문에서 복원해야 한다.
        Customer restored = customerPersistencePort.findById(customerId).orElseThrow();

        assertThat(restored.getEmail()).isEqualTo("ph18-seed@example.com");
        assertThat(restored.getPhoneNumber()).isEqualTo("01012345678");

        assertThat(customerPersistencePort.existsByEmail("ph18-seed@example.com"))
                .isTrue();

        // 같은 키로 다시 실행해도 기존 암호문이 변경되지 않아야 한다.
        runner.runOnce();

        Map<String, Object> afterRerun = readColumns(customerId);

        assertThat(afterRerun.get("email_ciphertext")).isEqualTo(after.get("email_ciphertext"));
        assertThat(afterRerun.get("phone_number_ciphertext")).isEqualTo(after.get("phone_number_ciphertext"));
        assertThat(afterRerun.get("email_search_hash")).isEqualTo(after.get("email_search_hash"));
    }

    @Test
    void backfillRepairsMissingHash() {
        long customerId = insertRawCustomer();

        CustomerPiiSeedBackfillRunner runner = runner();
        runner.runOnce();

        jdbcTemplate.update(
                """
                UPDATE customer
                SET email_search_hash = NULL
                WHERE customer_id = ?
                """,
                customerId);

        runner.runOnce();

        Map<String, Object> row = readColumns(customerId);

        assertThat(row.get("email_search_hash"))
                .isEqualTo(crypto.emailBlindIndex().hash("ph18-seed@example.com"));
    }

    @Test
    void rejectsInconsistentCiphertextAfterFirstFiftyRows() {
        // 백필이 필요한 기존 평문 고객
        long legacyCustomerId = insertRawCustomer();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 암호화 데이터가 있는 고객 51명 생성
        for (int i = 0; i < 51; i++) {
            String userId = "ph18f" + suffix + i;
            String email = "ph18-" + suffix + "-" + i + "@example.com";

            jdbcTemplate.update(
                    """
                INSERT INTO customer (
                    user_id, password_hash, user_name,
                    birth_date, email, phone_number,
                    joined_at, created_at, updated_at
                ) VALUES (
                    ?, ?, '검증고객',
                    '1990-01-01', ?, '01012345678',
                    NOW(6), NOW(6), NOW(6)
                )
                """,
                    userId,
                    "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                    email);

            // 마지막 고객의 이메일 암호문에만 다른 값을 저장한다.
            String encryptedEmail = crypto.cipher().encrypt(i == 50 ? "wrong@example.com" : email, EMAIL);

            jdbcTemplate.update(
                    """
                UPDATE customer
                SET email_ciphertext = ?,
                    phone_number_ciphertext = ?,
                    email_search_hash = ?
                WHERE user_id = ?
                """,
                    encryptedEmail,
                    crypto.cipher().encrypt("01012345678", PHONE_NUMBER),
                    crypto.emailBlindIndex().hash(email),
                    userId);
        }

        assertThatThrownBy(() -> runner().runOnce())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기존 개인정보 암호문 검증에 실패");

        // 사전 검사에 실패했으므로 실제 백필이 시작되지 않아야 한다.
        assertThat(readColumns(legacyCustomerId).get("email_ciphertext")).isNull();
    }

    private CustomerPiiSeedBackfillRunner runner() {
        return new CustomerPiiSeedBackfillRunner(
                backfillService, new CustomerPiiVerificationService(jdbcTemplate, crypto));
    }

    private long insertRawCustomer() {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    user_id,
                    password_hash,
                    user_name,
                    birth_date,
                    email,
                    phone_number,
                    login_failure_count,
                    account_locked,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (
                    ?,
                    ?,
                    '홍길동',
                    '1990-01-01',
                    ?,
                    ?,
                    0,
                    FALSE,
                    '2026-10-27 10:00:00',
                    '2026-10-27 10:00:00',
                    '2026-10-27 10:00:00'
                )
                """,
                "ph18seedcompat",
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "ph18-seed@example.com",
                "01012345678");

        return jdbcTemplate.queryForObject(
                """
                SELECT customer_id
                FROM customer
                WHERE user_id = ?
                """,
                Long.class,
                "ph18seedcompat");
    }

    private Map<String, Object> readColumns(long customerId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT email_ciphertext,
                       phone_number_ciphertext,
                       email_search_hash
                FROM customer
                WHERE customer_id = ?
                """,
                customerId);
    }
}
