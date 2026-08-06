package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountType;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("계좌번호 채번 Repository 통합 테스트")
class AccountNumberSequenceJpaRepositoryTest
        extends IntegrationTestSupport {

    @Autowired
    private AccountNumberSequenceJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("productId가 NULL인 입출금계좌 채번 행을 비관적 락으로 조회한다")
    void findsDemandDepositSequenceForUpdate() {
        // given
        insertDemandDepositSequence(0L);

        // when
        Optional<AccountNumberSequenceJpaEntity> result =
                repository.findDemandDepositForUpdate(
                        "088",
                        AccountType.DEMAND_DEPOSIT
                );

        // then
        assertThat(result).isPresent();

        AccountNumberSequenceJpaEntity entity =
                result.orElseThrow();

        assertThat(entity.getBankCode()).isEqualTo("088");
        assertThat(entity.getAccountType())
                .isEqualTo(AccountType.DEMAND_DEPOSIT);
        assertThat(entity.getProductId()).isNull();
        assertThat(entity.getProductPrefix()).isEqualTo("10");
        assertThat(entity.getLastSequence()).isZero();
    }

    @Test
    @DisplayName("상품 ID가 일치하는 정기예금 채번 행을 비관적 락으로 조회한다")
    void findsTimeDepositSequenceForUpdate() {
        //given
        Long productId = findProductId("PRD_BASIC_DEP");

        insertProductAccountSequence(
                productId,
                AccountType.TIME_DEPOSIT,
                "20",
                3L
        );

        // when
        Optional<AccountNumberSequenceJpaEntity> result =
                repository.findProductAccountForUpdate(
                        "088",
                        AccountType.TIME_DEPOSIT,
                        productId
                );

        // then
        assertThat(result).isPresent();

        AccountNumberSequenceJpaEntity entity =
                result.orElseThrow();

        assertThat(entity.getBankCode())
                .isEqualTo("088");

        assertThat(entity.getAccountType())
                .isEqualTo(AccountType.TIME_DEPOSIT);

        assertThat(entity.getProductId())
                .isEqualTo(productId);

        assertThat(entity.getProductPrefix())
                .isEqualTo("20");

        assertThat(entity.getLastSequence())
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("조건에 맞는 채번 행이 없으면 빈 Optional을 반환한다")
    void returnsEmptyWhenSequenceDoesNotExist() {
        Optional<AccountNumberSequenceJpaEntity> result =
                repository.findProductAccountForUpdate(
                        "088",
                        AccountType.TIME_DEPOSIT,
                        Long.MAX_VALUE
                );

        assertThat(result).isEmpty();
    }

    private Long findProductId(String productCode) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT product_id
                        FROM product
                        WHERE product_code = ?
                        """,
                Long.class,
                productCode
        );
    }

    private void insertDemandDepositSequence(
            long lastSequence
    ) {
        jdbcTemplate.update("""
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
                            '088',
                            'DEMAND_DEPOSIT',
                            NULL,
                            '10',
                            ?,
                            CURRENT_TIMESTAMP(6),
                            CURRENT_TIMESTAMP(6)
                        )
                        """,
                lastSequence
        );
    }

    private void insertProductAccountSequence(
            long productId,
            AccountType accountType,
            String productPrefix,
            long lastSequence
    ) {
        jdbcTemplate.update("""
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
                            '088',
                            ?,
                            ?,
                            ?,
                            ?,
                            CURRENT_TIMESTAMP(6),
                            CURRENT_TIMESTAMP(6)
                        )
                        """,
                accountType.name(),
                productId,
                productPrefix,
                lastSequence
        );
    }
}