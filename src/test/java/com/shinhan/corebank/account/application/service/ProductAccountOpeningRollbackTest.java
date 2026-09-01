package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ProductAccountOpeningRollbackTest extends IntegrationTestSupport {

    private static final String PRODUCT_CODE = "PRD_BASIC_DEP";

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private ProductAccountOpeningUseCase productAccountOpeningUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @MockitoBean
    private AccountPersistencePort accountPersistencePort;

    private Long customerId;

    private AccountNumberSequenceTestFixture sequenceFixture;

    private Long productId;

    @BeforeEach
    void setUp() {
        sequenceFixture = new AccountNumberSequenceTestFixture(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        if (productId != null) {
            sequenceFixture.deleteProductAccountSequence(productId, AccountType.TIME_DEPOSIT);
        }

        if (customerId != null) {
            customerTestFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("계좌 저장에 실패하면 계좌번호 채번도 롤백된다")
    void rollbackSequenceWhenAccountSaveFails() {
        // given
        productId = sequenceFixture.findProductId(PRODUCT_CODE);

        customerId = customerTestFixture.createCustomer();

        when(accountPersistencePort.save(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("forced account save failure"));

        sequenceFixture.resetProductAccountSequence(
                productId, AccountType.TIME_DEPOSIT, AccountNumberSequenceTestFixture.TIME_DEPOSIT_PREFIX, 100L);

        ProductAccountOpeningCommand command = new ProductAccountOpeningCommand(
                customerId,
                productId,
                AccountType.TIME_DEPOSIT,
                PASSWORD_HASH,
                LocalDate.now(clock).plusYears(1));

        // when
        Throwable thrown = catchThrowable(() -> productAccountOpeningUseCase.open(command));

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("forced account save failure");

        Long lastSequence = findProductAccountLastSequence(productId, AccountType.TIME_DEPOSIT);

        assertThat(lastSequence).isEqualTo(100L);

        Integer accountCount = jdbcTemplate.queryForObject(
                """
                                SELECT COUNT(*)
                                FROM account
                                WHERE customer_id = ?
                                """,
                Integer.class,
                customerId);

        assertThat(accountCount).isZero();
    }

    private Long findProductAccountLastSequence(Long productId, AccountType accountType) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT last_sequence
                        FROM account_number_sequence
                        WHERE bank_code = ?
                          AND account_type = ?
                          AND product_id = ?
                        """,
                Long.class,
                "088",
                accountType.name(),
                productId);
    }
}
