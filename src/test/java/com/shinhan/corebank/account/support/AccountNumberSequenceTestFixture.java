package com.shinhan.corebank.account.support;

import com.shinhan.corebank.account.domain.AccountNumberPolicy;
import com.shinhan.corebank.account.domain.AccountType;
import org.springframework.jdbc.core.JdbcTemplate;

public class AccountNumberSequenceTestFixture {

    //prefix 테스트 상수 추가
    public static final String DEMAND_DEPOSIT_PREFIX = "10";
    public static final String TIME_DEPOSIT_PREFIX = "20";
    public static final String INSTALLMENT_SAVINGS_PREFIX = "30";

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