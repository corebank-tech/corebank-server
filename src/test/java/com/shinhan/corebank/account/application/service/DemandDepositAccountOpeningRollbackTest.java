package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.in.DemandDepositAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.DemandDepositAccountOpeningUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class DemandDepositAccountOpeningRollbackTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private DemandDepositAccountOpeningUseCase demandDepositAccountOpeningUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AccountPersistencePort accountPersistencePort;

    private AccountNumberSequenceTestFixture sequenceFixture;

    private Long customerId;

    @BeforeEach
    void setUp() {
        sequenceFixture = new AccountNumberSequenceTestFixture(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        sequenceFixture.deleteDemandDepositSequence();

        if (customerId != null) {
            customerTestFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("입출금계좌 저장에 실패하면 계좌번호 채번도 롤백된다")
    void rollbackSequenceWhenAccountSaveFails() {
        // given
        customerId = customerTestFixture.createCustomer();

        sequenceFixture.resetDemandDepositSequence(100L);

        DemandDepositAccountOpeningCommand command = new DemandDepositAccountOpeningCommand(customerId, PASSWORD_HASH);

        when(accountPersistencePort.save(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("forced account save failure"));

        // when
        Throwable thrown = catchThrowable(() -> demandDepositAccountOpeningUseCase.open(command));

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("forced account save failure");

        Long lastSequence = sequenceFixture.findDemandDepositLastSequence();

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
}
