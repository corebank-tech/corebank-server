package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningUseCase;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ProductAccountOpeningIntegrationTest
        extends IntegrationTestSupport {

    private static final String PRODUCT_CODE =
            "PRD_BASIC_DEP";

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private ProductAccountOpeningUseCase
            productAccountOpeningUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private AccountNumberSequenceTestFixture sequenceFixture;

    @BeforeEach
    void setUp() {
        sequenceFixture =
                new AccountNumberSequenceTestFixture(
                        jdbcTemplate
                );
    }

    @Test
    @DisplayName("정기예금 상품 계좌를 개설하면 채번된 계좌가 DB에 저장된다")
    void openTimeDepositAccount() {
        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Long productId =
                sequenceFixture.findProductId(
                        PRODUCT_CODE
                );

        sequenceFixture.resetProductAccountSequence(
                productId,
                AccountType.TIME_DEPOSIT,
                AccountNumberSequenceTestFixture.TIME_DEPOSIT_PREFIX,
                0L
        );

        LocalDate maturityDate =
                LocalDate.now().plusYears(1);

        ProductAccountOpeningCommand command =
                new ProductAccountOpeningCommand(
                        customerId,
                        productId,
                        AccountType.TIME_DEPOSIT,
                        PASSWORD_HASH,
                        maturityDate
                );

        // when
        AccountOpeningResult result =
                productAccountOpeningUseCase.open(command);

        entityManager.flush();

        // then
        assertThat(result.accountId()).isNotNull();

        assertThat(result.accountNumber())
                .isEqualTo("088200000001");

        AccountRow row = jdbcTemplate.queryForObject(
                """
                SELECT
                    account_id,
                    account_number,
                    customer_id,
                    product_id,
                    account_type,
                    balance,
                    status,
                    maturity_date
                FROM account
                WHERE account_id = ?
                """,
                (rs, rowNum) -> new AccountRow(
                        rs.getLong("account_id"),
                        rs.getString("account_number"),
                        rs.getLong("customer_id"),
                        rs.getLong("product_id"),
                        rs.getString("account_type"),
                        rs.getLong("balance"),
                        rs.getString("status"),
                        rs.getObject(
                                "maturity_date",
                                LocalDate.class
                        )
                ),
                result.accountId()
        );

        assertThat(row).isNotNull();

        assertThat(row.accountId())
                .isEqualTo(result.accountId());

        assertThat(row.accountNumber())
                .isEqualTo(result.accountNumber());

        assertThat(row.customerId())
                .isEqualTo(customerId);

        assertThat(row.productId())
                .isEqualTo(productId);

        assertThat(row.accountType())
                .isEqualTo(
                        AccountType.TIME_DEPOSIT.name()
                );

        assertThat(row.balance()).isZero();

        assertThat(row.status())
                .isEqualTo(
                        AccountStatus.ACTIVE.name()
                );

        assertThat(row.maturityDate())
                .isEqualTo(maturityDate);
    }

    private record AccountRow(
            Long accountId,
            String accountNumber,
            Long customerId,
            Long productId,
            String accountType,
            long balance,
            String status,
            LocalDate maturityDate
    ) {
    }
}