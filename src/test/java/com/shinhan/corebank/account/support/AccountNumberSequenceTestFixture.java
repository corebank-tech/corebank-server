package com.shinhan.corebank.account.support;

import com.shinhan.corebank.account.domain.AccountNumberPolicy;
import com.shinhan.corebank.account.domain.AccountType;
import org.springframework.jdbc.core.JdbcTemplate;

public class AccountNumberSequenceTestFixture {

    // 운영 시드(R__seed_master_data.sql)가 10(입출금) / 2x(예금) / 3x(적금) 를 선점하고 있다.
    // 테스트가 임의로 만든 상품에 그 prefix 를 쓰면 uk_account_number_sequence_prefix 에
    // 걸리므로, 테스트 전용으로 9x 대역을 쓴다.
    public static final String DEMAND_DEPOSIT_PREFIX = "90";
    public static final String TIME_DEPOSIT_PREFIX = "91";
    public static final String INSTALLMENT_SAVINGS_PREFIX = "92";

    private final JdbcTemplate jdbcTemplate;

    public AccountNumberSequenceTestFixture(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetDemandDepositSequence(long lastSequence) {
        deleteDemandDepositSequence();

        jdbcTemplate.update(
                """
                INSERT INTO account_number_sequence (
                    bank_code,
                    account_type,
                    product_id,
                    product_prefix,
                    last_sequence,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    'DEMAND_DEPOSIT',
                    NULL,
                    ?,
                    ?,
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6)
                )
                """,
                AccountNumberPolicy.BANK_CODE,
                DEMAND_DEPOSIT_PREFIX,
                lastSequence
        );
    }

    public void resetProductAccountSequence(
            Long productId,
            AccountType accountType,
            String productPrefix,
            long lastSequence
    ) {
        deleteProductAccountSequence(
                productId,
                accountType
        );

        jdbcTemplate.update(
                """
                INSERT INTO account_number_sequence (
                    bank_code,
                    account_type,
                    product_id,
                    product_prefix,
                    last_sequence,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6)
                )
                """,
                AccountNumberPolicy.BANK_CODE,
                accountType.name(),
                productId,
                productPrefix,
                lastSequence
        );
    }

    public void deleteDemandDepositSequence() {
        jdbcTemplate.update(
                """
                DELETE FROM account_number_sequence
                 WHERE bank_code = ?
                   AND account_type = 'DEMAND_DEPOSIT'
                   AND product_id IS NULL
                """,
                AccountNumberPolicy.BANK_CODE
        );
    }

    public void deleteProductAccountSequence(
            Long productId,
            AccountType accountType
    ) {
        jdbcTemplate.update(
                """
                DELETE FROM account_number_sequence
                 WHERE bank_code = ?
                   AND account_type = ?
                   AND product_id = ?
                """,
                AccountNumberPolicy.BANK_CODE,
                accountType.name(),
                productId
        );
    }

    //queryForObject() -> query()로 교체
    public long findDemandDepositLastSequence() {
        return jdbcTemplate.query(
                        """
                        SELECT last_sequence
                          FROM account_number_sequence
                         WHERE bank_code = ?
                           AND account_type = 'DEMAND_DEPOSIT'
                           AND product_id IS NULL
                        """,
                        (rs, rowNum) ->
                                rs.getLong("last_sequence"),
                        AccountNumberPolicy.BANK_CODE
                )
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "테스트용 입출금계좌 채번 행이 존재하지 않습니다."
                        )
                );
    }

    public Long findProductId(String productCode) {
        return jdbcTemplate.query(
                        """
                        SELECT product_id
                          FROM product
                         WHERE product_code = ?
                        """,
                        (rs, rowNum) ->
                                rs.getLong("product_id"),
                        productCode
                )
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "테스트에 필요한 상품이 존재하지 않습니다: "
                                        + productCode
                        )
                );
    }
}