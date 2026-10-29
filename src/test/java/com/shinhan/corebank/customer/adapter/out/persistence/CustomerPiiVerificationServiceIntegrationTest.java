package com.shinhan.corebank.customer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import com.shinhan.piimaintenance.CustomerPiiVerificationService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class CustomerPiiVerificationServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CustomerPersistencePort customers;

    @Autowired
    private CustomerPiiCrypto crypto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void detectsValidCustomer() {
        long id = customer("ph18verifyok", "ok@ph18verify.test");

        var result = verifier().verifyRange(id - 1, id);

        assertThat(result.checkedRows()).isEqualTo(1);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void detectsMissingCiphertext() {
        long id = customer("ph18verifymis", "missing@ph18verify.test");

        jdbc.update(
                """
                UPDATE customer
                SET phone_number_ciphertext = NULL
                WHERE customer_id = ?
                """,
                id);

        var result = verifier().verifyRange(id - 1, id);

        assertThat(result.missingRows()).isEqualTo(1);
        assertThat(result.passed()).isFalse();
    }

    @Test
    void detectsPlaintextMismatchAndHashMismatch() {
        long id = customer("ph18verifydiff", "original@ph18verify.test");

        jdbc.update(
                """
                UPDATE customer
                SET email = ?
                WHERE customer_id = ?
                """,
                "changed@ph18verify.test",
                id);

        var result = verifier().verifyRange(id - 1, id);

        assertThat(result.emailMismatchRows()).isEqualTo(1);
        assertThat(result.hashMismatchRows()).isEqualTo(1);
        assertThat(result.passed()).isFalse();

        // 키 검증 단계에서도 기존 데이터 불일치를 감지해야 한다.
        assertThat(result.existingDataConsistent()).isFalse();
    }

    @Test
    void detectsCorruptedCiphertext() {
        long id = customer("ph18verifybad", "bad@ph18verify.test");

        jdbc.update(
                """
                UPDATE customer
                SET email_ciphertext = ?
                WHERE customer_id = ?
                """,
                "v1:invalid",
                id);

        var result = verifier().verifyRange(id - 1, id);

        assertThat(result.cryptoFailureRows()).isEqualTo(1);
        assertThat(result.passed()).isFalse();
        assertThat(result.existingDataConsistent()).isFalse();
    }

    @Test
    void allowsMissingColumnsDuringPreBackfillCheck() {
        long id = customer("ph18verifypre", "pre@ph18verify.test");

        jdbc.update(
                """
                UPDATE customer
                SET email_ciphertext = NULL,
                    phone_number_ciphertext = NULL,
                    email_search_hash = NULL
                WHERE customer_id = ?
                """,
                id);

        var verifier = verifier();

        // 백필 전에는 아직 암호화되지 않은 행을 허용한다.
        // 전체 검증에서는 동일한 행을 실패로 판정한다.
        assertThat(verifier.verifyExistingEncryptedRows().existingDataConsistent())
                .isTrue();

        assertThat(verifier.verifyRange(id - 1, id).passed()).isFalse();
    }

    private CustomerPiiVerificationService verifier() {
        return new CustomerPiiVerificationService(jdbc, crypto);
    }

    private long customer(String userId, String email) {
        Customer saved = customers.save(Customer.register(
                userId,
                null,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "검증고객",
                LocalDate.of(1990, 1, 1),
                email,
                "01012345678",
                LocalDateTime.of(2026, 10, 29, 9, 0)));

        entityManager.flush();
        entityManager.clear();

        return saved.getCustomerId();
    }
}
