package com.shinhan.piimaintenance;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.CorebankApplication;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.customer.adapter.out.security.CustomerEmailBlindIndex;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = CorebankApplication.class)
@TestPropertySource(properties = "ph18.maintenance.integration.isolated=true")
class CustomerPiiMaintenanceApplicationIntegrationTest extends IntegrationTestSupport {

    // 실제 운영 키가 아닌 테스트 전용 고정 키.
    private static final String AES_KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

    private static final String HMAC_KEY = "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=";

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final String TEST_EMAIL = "ph18-maintenance@example.com";
    private static final String PHONE = "01012345678";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcConnectionDetails connectionDetails;

    private Long customerId;
    private String previousDevtoolsRestart;

    @BeforeEach
    void setUp() {
        previousDevtoolsRestart = System.getProperty("spring.devtools.restart.enabled");

        String userId = "ph18m" + UUID.randomUUID().toString().substring(0, 8);

        jdbc.update(
                """
                INSERT INTO customer (
                    user_id,
                    password_hash,
                    user_name,
                    birth_date,
                    email,
                    phone_number,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (
                    ?, ?, '유지보수고객', '1990-01-01',
                    ?, ?, NOW(6), NOW(6), NOW(6)
                )
                """,
                userId,
                PASSWORD_HASH,
                TEST_EMAIL,
                PHONE);

        customerId = jdbc.queryForObject("SELECT customer_id FROM customer WHERE user_id = ?", Long.class, userId);
    }

    @AfterEach
    void cleanUp() {
        if (customerId != null) {
            jdbc.update("DELETE FROM customer WHERE customer_id = ?", customerId);
        }

        if (previousDevtoolsRestart == null) {
            System.clearProperty("spring.devtools.restart.enabled");
        } else {
            System.setProperty("spring.devtools.restart.enabled", previousDevtoolsRestart);
        }
    }

    @Test
    void backfillsAndVerifiesUsingActualMaintenanceApplication() throws Exception {
        // 과거 서버가 저장한 고객: 평문만 존재하고 암호화 컬럼은 NULL.
        Map<String, Object> before = readCustomer();

        assertThat(before.get("email_ciphertext")).isNull();
        assertThat(before.get("phone_number_ciphertext")).isNull();
        assertThat(before.get("email_search_hash")).isNull();

        // 실제 Spring Boot 메인 진입점 → 유지보수 앱 → 백필 실행.
        CorebankApplication.main(maintenanceArgs("backfill", expectedHost()));

        Map<String, Object> after = readCustomer();

        assertThat(after.get("email")).isEqualTo(TEST_EMAIL);
        assertThat(after.get("phone_number")).isEqualTo(PHONE);

        String emailCiphertext = (String) after.get("email_ciphertext");
        String phoneCiphertext = (String) after.get("phone_number_ciphertext");
        String emailHash = (String) after.get("email_search_hash");

        assertThat(emailCiphertext).startsWith("v1:");
        assertThat(phoneCiphertext).startsWith("v1:");
        assertThat(emailHash).isNotNull();

        CustomerPiiCipher cipher = new CustomerPiiCipher(Base64.getDecoder().decode(AES_KEY));

        CustomerEmailBlindIndex blindIndex =
                new CustomerEmailBlindIndex(Base64.getDecoder().decode(HMAC_KEY));

        assertThat(cipher.decrypt(emailCiphertext, EMAIL)).isEqualTo(TEST_EMAIL);

        assertThat(cipher.decrypt(phoneCiphertext, PHONE_NUMBER)).isEqualTo(PHONE);

        assertThat(emailHash).isEqualTo(blindIndex.hash(TEST_EMAIL));

        // 독립된 유지보수 프로세스의 전체 검증 경로 실행.
        CorebankApplication.main(maintenanceArgs("verify", expectedHost()));

        // 검증은 고객 데이터를 변경하지 않아야 한다.
        Map<String, Object> verified = readCustomer();

        assertThat(verified.get("email_ciphertext")).isEqualTo(emailCiphertext);
        assertThat(verified.get("phone_number_ciphertext")).isEqualTo(phoneCiphertext);
        assertThat(verified.get("email_search_hash")).isEqualTo(emailHash);
    }

    @Test
    void rejectsUnexpectedDatabaseWithoutChangingCustomer() {
        Map<String, Object> before = readCustomer();

        // 실제 Testcontainers DB와 일치하지 않는 호스트를 지정한다.
        assertThatThrownBy(() -> CorebankApplication.main(maintenanceArgs("backfill", "unexpected-db-host.invalid")))
                .hasStackTraceContaining("PII 작업 대상 DB");

        Map<String, Object> after = readCustomer();

        // 잘못된 대상으로 판단되면 암호화 컬럼을 변경하지 않는다.
        assertThat(after.get("email_ciphertext")).isEqualTo(before.get("email_ciphertext"));

        assertThat(after.get("phone_number_ciphertext")).isEqualTo(before.get("phone_number_ciphertext"));

        assertThat(after.get("email_search_hash")).isEqualTo(before.get("email_search_hash"));
    }

    private String[] maintenanceArgs(String action, String host) {
        return new String[] {
            "--spring.profiles.active=pii-maintenance",
            "--spring.datasource.url=" + connectionDetails.getJdbcUrl(),
            "--spring.datasource.username=" + connectionDetails.getUsername(),
            "--spring.datasource.password=" + connectionDetails.getPassword(),
            "--app.pii.enabled=true",
            "--app.pii.read-encrypted=false",
            "--app.pii.maintenance.action=" + action,
            "--app.pii.maintenance.expected-db-host=" + host,
            "--app.pii.maintenance.expected-db-name=" + expectedDatabaseName(),
            "--APP_PII_AES_KEY_BASE64=" + AES_KEY,
            "--APP_PII_HMAC_KEY_BASE64=" + HMAC_KEY,
            "--spring.flyway.enabled=false",
            "--spring.main.banner-mode=off"
        };
    }

    private String expectedHost() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();

            return URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
        }
    }

    private String expectedDatabaseName() {
        return jdbc.queryForObject("SELECT DATABASE()", String.class);
    }

    private Map<String, Object> readCustomer() {
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
}
