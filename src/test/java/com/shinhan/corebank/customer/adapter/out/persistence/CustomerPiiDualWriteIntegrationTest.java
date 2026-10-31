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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(
        properties = {
            "app.pii.enabled=true",
            "APP_PII_AES_KEY_BASE64=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "APP_PII_HMAC_KEY_BASE64=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
        })
class CustomerPiiDualWriteIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private CustomerPiiCrypto crypto;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveCustomerWritesEncryptedColumns() {
        Customer customer = Customer.register(
                "ph18-dual-write-user",
                null,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "ph18@example.com",
                "01012345678",
                LocalDateTime.of(2026, 10, 26, 10, 0));

        Customer saved = customerPersistencePort.save(customer);

        entityManager.flush();
        entityManager.clear();

        Object[] row = readContactColumns(saved.getCustomerId());

        assertThat(row[0]).isEqualTo("ph18@example.com");
        assertThat(row[1]).isEqualTo("01012345678");

        String encryptedEmail = (String) row[2];
        String encryptedPhone = (String) row[3];
        String emailHash = (String) row[4];

        assertThat(encryptedEmail).startsWith("v1:");
        assertThat(encryptedPhone).startsWith("v1:");

        assertThat(crypto.cipher().decrypt(encryptedEmail, EMAIL)).isEqualTo("ph18@example.com");

        assertThat(crypto.cipher().decrypt(encryptedPhone, PHONE_NUMBER)).isEqualTo("01012345678");

        assertThat(emailHash).isEqualTo(crypto.emailBlindIndex().hash("ph18@example.com"));
    }

    @Test
    void updateContactInfoUpdatesEncryptedColumns() {
        Customer customer = Customer.register(
                "ph18-update-user",
                null,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "김철수",
                LocalDate.of(1995, 5, 5),
                "before@example.com",
                "01011112222",
                LocalDateTime.of(2026, 10, 26, 10, 0));

        Customer saved = customerPersistencePort.save(customer);

        entityManager.flush();
        entityManager.clear();

        Customer loaded =
                customerPersistencePort.findByIdForUpdate(saved.getCustomerId()).orElseThrow();

        loaded.changeContactInfo("01099998888", "after@example.com");

        customerPersistencePort.updateContactInfo(loaded);

        entityManager.flush();
        entityManager.clear();

        Object[] row = readContactColumns(saved.getCustomerId());

        assertThat(row[0]).isEqualTo("after@example.com");
        assertThat(row[1]).isEqualTo("01099998888");

        assertThat(crypto.cipher().decrypt((String) row[2], EMAIL)).isEqualTo("after@example.com");

        assertThat(crypto.cipher().decrypt((String) row[3], PHONE_NUMBER)).isEqualTo("01099998888");

        assertThat(row[4]).isEqualTo(crypto.emailBlindIndex().hash("after@example.com"));
    }

    private Object[] readContactColumns(Long customerId) {
        return (Object[]) entityManager
                .createNativeQuery(
                        """
                        SELECT email,
                               phone_number,
                               email_ciphertext,
                               phone_number_ciphertext,
                               email_search_hash
                        FROM customer
                        WHERE customer_id = :customerId
                        """)
                .setParameter("customerId", customerId)
                .getSingleResult();
    }
}
