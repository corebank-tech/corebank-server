package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountNumberPolicy;
import com.shinhan.corebank.account.domain.AccountType;

import java.util.Optional;

import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture.DEMAND_DEPOSIT_PREFIX;
import static com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture.TIME_DEPOSIT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("계좌번호 채번 Repository 통합 테스트")
class AccountNumberSequenceJpaRepositoryTest
        extends IntegrationTestSupport {

    @Autowired
    private AccountNumberSequenceJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AccountNumberSequenceTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture =
                new AccountNumberSequenceTestFixture(
                        jdbcTemplate
                );
    }

    @Test
    @DisplayName("productId가 NULL인 입출금계좌 채번 행을 비관적 락으로 조회한다")
    void findsDemandDepositSequenceForUpdate() {
        // given
        fixture.resetDemandDepositSequence(0L);

        // when
        Optional<AccountNumberSequenceJpaEntity> result =
                repository.findDemandDepositForUpdate(
                        AccountNumberPolicy.BANK_CODE,
                        AccountType.DEMAND_DEPOSIT
                );

        // then
        assertThat(result).isPresent();

        AccountNumberSequenceJpaEntity entity =
                result.orElseThrow();

        assertThat(entity.getBankCode())
                .isEqualTo(AccountNumberPolicy.BANK_CODE);

        assertThat(entity.getAccountType())
                .isEqualTo(AccountType.DEMAND_DEPOSIT);

        assertThat(entity.getProductId())
                .isNull();

        assertThat(entity.getProductPrefix())
                .isEqualTo(DEMAND_DEPOSIT_PREFIX);

        assertThat(entity.getLastSequence())
                .isZero();
    }

    @Test
    @DisplayName("상품 ID가 일치하는 정기예금 채번 행을 비관적 락으로 조회한다")
    void findsTimeDepositSequenceForUpdate() {
        // given
        Long productId =
                fixture.findProductId("PRD_BASIC_DEP");

        fixture.resetProductAccountSequence(
                productId,
                AccountType.TIME_DEPOSIT,
                "20",
                3L
        );

        // when
        Optional<AccountNumberSequenceJpaEntity> result =
                repository.findProductAccountForUpdate(
                        AccountNumberPolicy.BANK_CODE,
                        AccountType.TIME_DEPOSIT,
                        productId
                );

        // then
        assertThat(result).isPresent();

        AccountNumberSequenceJpaEntity entity =
                result.orElseThrow();

        assertThat(entity.getBankCode())
                .isEqualTo(AccountNumberPolicy.BANK_CODE);

        assertThat(entity.getAccountType())
                .isEqualTo(AccountType.TIME_DEPOSIT);

        assertThat(entity.getProductId())
                .isEqualTo(productId);

        assertThat(entity.getProductPrefix())
                .isEqualTo(TIME_DEPOSIT_PREFIX);

        assertThat(entity.getLastSequence())
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("조건에 맞는 채번 행이 없으면 빈 Optional을 반환한다")
    void returnsEmptyWhenSequenceDoesNotExist() {
        // given
        Long productId =
                fixture.findProductId("PRD_BASIC_DEP");

        fixture.deleteProductAccountSequence(
                productId,
                AccountType.TIME_DEPOSIT
        );

        // when
        Optional<AccountNumberSequenceJpaEntity> result =
                repository.findProductAccountForUpdate(
                        AccountNumberPolicy.BANK_CODE,
                        AccountType.TIME_DEPOSIT,
                        productId
                );

        // then
        assertThat(result).isEmpty();
    }
}