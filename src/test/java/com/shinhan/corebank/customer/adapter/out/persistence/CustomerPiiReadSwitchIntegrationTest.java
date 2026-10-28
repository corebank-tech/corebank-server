package com.shinhan.corebank.customer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.application.port.out.CustomerAdminQueryPort;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.CustomerSearchCondition;
import com.shinhan.corebank.customer.domain.model.Customer;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
class CustomerPiiReadSwitchIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private CustomerAdminQueryPort customerAdminQueryPort;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void readsEncryptedColumnsInsteadOfPlaintext() {
        Customer saved = createCustomer();

        entityManager.flush();
        entityManager.clear();

        // 읽기가 평문에 의존하는지 검증하기 위해 테스트 DB에서만 값을 변경
        jdbcTemplate.update(
                """
                UPDATE customer
                SET email = ?,
                    phone_number = ?
                WHERE customer_id = ?
                """,
                "decoy-read@example.com",
                "01099999999",
                saved.getCustomerId());

        entityManager.clear();

        Customer found = customerPersistencePort.findById(saved.getCustomerId()).orElseThrow();

        // DB의 평문이 아니라 원래 암호화된 정보를 복원해야 한다.
        assertThat(found.getEmail()).isEqualTo("ph18-read@example.com");
        assertThat(found.getPhoneNumber()).isEqualTo("01012345678");

        // 이메일 중복 검사가 평문 컬럼이 아닌 HMAC을 사용해야 한다.
        assertThat(customerPersistencePort.existsByEmail("ph18-read@example.com"))
                .isTrue();
        assertThat(customerPersistencePort.existsByEmail("decoy-read@example.com"))
                .isFalse();

        // 관리자 정확 일치 검색도 HMAC으로 찾아야 한다.
        var page = customerAdminQueryPort.search(
                new CustomerSearchCondition(null, null, "ph18-read@example.com", null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);

        assertThat(page.getContent().get(0).email()).isEqualTo("ph18-read@example.com");

        assertThat(page.getContent().get(0).phoneNumber()).isEqualTo("01012345678");

        // 관리자 상세 조회에서도 복호화된 정보가 반환되어야 한다.
        var detail = customerAdminQueryPort.findById(saved.getCustomerId()).orElseThrow();

        assertThat(detail.email()).isEqualTo("ph18-read@example.com");
        assertThat(detail.phoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void rejectsMissingEncryptedEmail() {
        Customer saved = createCustomer();

        entityManager.flush();
        entityManager.clear();

        jdbcTemplate.update(
                """
                UPDATE customer
                SET email_ciphertext = NULL
                WHERE customer_id = ?
                """,
                saved.getCustomerId());

        entityManager.clear();

        // 백필 누락 시 평문으로 대체하면 안 된다.
        assertThatIllegalStateException().isThrownBy(() -> customerPersistencePort.findById(saved.getCustomerId()));
    }

    @Test
    void contactUpdateWorksWithEncryptedReads() {
        Customer saved = createCustomer();

        entityManager.flush();
        entityManager.clear();

        Customer loaded =
                customerPersistencePort.findByIdForUpdate(saved.getCustomerId()).orElseThrow();

        loaded.changeContactInfo("01088887777", "ph18-updated@example.com");

        customerPersistencePort.updateContactInfo(loaded);

        entityManager.flush();
        entityManager.clear();

        Customer found = customerPersistencePort.findById(saved.getCustomerId()).orElseThrow();

        assertThat(found.getEmail()).isEqualTo("ph18-updated@example.com");
        assertThat(found.getPhoneNumber()).isEqualTo("01088887777");

        assertThat(customerPersistencePort.existsByEmail("ph18-updated@example.com"))
                .isTrue();

        assertThat(customerPersistencePort.existsByEmail("ph18-read@example.com"))
                .isFalse();
    }

    private Customer createCustomer() {
        return customerPersistencePort.save(Customer.register(
                "ph18readcustomer",
                null,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "ph18-read@example.com",
                "01012345678",
                LocalDateTime.of(2026, 10, 27, 10, 0)));
    }
}
