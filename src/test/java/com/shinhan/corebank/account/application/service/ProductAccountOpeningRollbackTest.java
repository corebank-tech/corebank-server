package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningUseCase;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ProductAccountOpeningRollbackTest
        extends IntegrationTestSupport {

    private static final String PRODUCT_CODE =
            "PRD_BASIC_DEP";

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final Long NON_EXISTENT_CUSTOMER_ID =
            999_999_999L;

    @Autowired
    private ProductAccountOpeningUseCase
            productAccountOpeningUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private AccountNumberSequenceTestFixture sequenceFixture;

    private Long productId;

    @BeforeEach
    void setUp() {
        sequenceFixture =
                new AccountNumberSequenceTestFixture(
                        jdbcTemplate
                );
    }

    @AfterEach
    void tearDown() {
        if (productId != null) {
            sequenceFixture.deleteProductAccountSequence(
                    productId,
                    AccountType.TIME_DEPOSIT
            );
        }
    }

    @Test
    @DisplayName("계좌 저장에 실패하면 계좌번호 채번도 롤백된다")
    void rollbackSequenceWhenAccountSaveFails() {
        // given
        productId =
                sequenceFixture.findProductId(
                        PRODUCT_CODE
                );

        sequenceFixture.resetProductAccountSequence(
                productId,
                AccountType.TIME_DEPOSIT,
                AccountNumberSequenceTestFixture.TIME_DEPOSIT_PREFIX,
                100L
        );

        ProductAccountOpeningCommand command =
                new ProductAccountOpeningCommand(
                        NON_EXISTENT_CUSTOMER_ID,
                        productId,
                        AccountType.TIME_DEPOSIT,
                        PASSWORD_HASH,
                        LocalDate.now(clock).plusYears(1)
                );

        // when
        Throwable thrown = catchThrowable(
                () -> productAccountOpeningUseCase.open(command)
        );

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(
                        SQLIntegrityConstraintViolationException.class
                );

        Throwable rootCause = thrown;

        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        assertThat(rootCause.getMessage())
                .contains("fk_account_customer");

        Long lastSequence =
                findProductAccountLastSequence(
                        productId,
                        AccountType.TIME_DEPOSIT
                );

        assertThat(lastSequence).isEqualTo(100L);

        Integer accountCount =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                FROM account
                                WHERE customer_id = ?
                                """,
                        Integer.class,
                        NON_EXISTENT_CUSTOMER_ID
                );

        assertThat(accountCount).isZero();
    }

    private Long findProductAccountLastSequence(
            Long productId,
            AccountType accountType
    ) {
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
                productId
        );
    }
}