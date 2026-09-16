package com.shinhan.corebank.autotransfer.adapter.out.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.autotransfer.application.port.out.DepositAccountInfo;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// AccountStatusAdapter는 @Profile("prod")라 test 프로필에선 스프링 빈으로 뜨지 않는다.
// 실제 DB 조회 로직 자체를 검증하기 위해 리포지토리만 주입받아 어댑터를 직접 생성해서 호출한다.
@Transactional
class AccountStatusAdapterTest extends IntegrationTestSupport {

    @Autowired
    AccountLookupJpaRepository accountLookupJpaRepository;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    private AccountStatusAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountStatusAdapter(accountLookupJpaRepository);
    }

    @Test
    @DisplayName("ACTIVE 상태 계좌는 true를 반환한다")
    void isActiveAccount_activeStatus_returnsTrue() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "ACTIVE");

        assertThat(adapter.isActiveAccount(accountId)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED 상태 계좌는 false를 반환한다")
    void isActiveAccount_suspendedStatus_returnsFalse() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "SUSPENDED");

        assertThat(adapter.isActiveAccount(accountId)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 계좌ID는 false를 반환한다")
    void isActiveAccount_notFound_returnsFalse() {
        assertThat(adapter.isActiveAccount(999_999_999L)).isFalse();
    }

    @Test
    @DisplayName("존재하는 계좌번호는 계좌유형과 만기일을 함께 반환한다")
    void findDepositAccountInfo_found_returnsAccountTypeAndMaturityDate() {
        Long customerId = insertCustomer();
        String accountNumber = nextAccountNumber();
        insertAccount(customerId, accountNumber, "ACTIVE");

        // 입출금계좌(DEMAND_DEPOSIT)는 ck_account_maturity 제약상 maturity_date가 항상 NULL이다
        assertThat(adapter.findDepositAccountInfo(accountNumber))
                .contains(new DepositAccountInfo(AccountType.DEMAND_DEPOSIT, null));
    }

    @Test
    @DisplayName("존재하지 않는 계좌번호는 빈 값을 반환한다")
    void findDepositAccountInfo_notFound_returnsEmpty() {
        assertThat(adapter.findDepositAccountInfo("999999999999")).isEmpty();
    }

    @Test
    @DisplayName("정기적금처럼 만기일이 있는 계좌는 만기일 값을 그대로 반환한다 (#418 리뷰 반영)")
    void findDepositAccountInfo_installmentSavings_returnsMaturityDate() {
        Long customerId = insertCustomer();
        String accountNumber = nextAccountNumber();
        LocalDate maturityDate = LocalDate.now().plusMonths(12);
        insertAccount(customerId, accountNumber, "ACTIVE", "INSTALLMENT_SAVINGS", maturityDate);

        assertThat(adapter.findDepositAccountInfo(accountNumber))
                .contains(new DepositAccountInfo(AccountType.INSTALLMENT_SAVINGS, maturityDate));
    }

    @Test
    @DisplayName("본인 소유 계좌면 true를 반환한다")
    void belongsToCustomer_owned_returnsTrue() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "ACTIVE");

        assertThat(adapter.belongsToCustomer(accountId, customerId)).isTrue();
    }

    @Test
    @DisplayName("타 고객 소유 계좌면 false를 반환한다")
    void belongsToCustomer_notOwned_returnsFalse() {
        Long ownerId = insertCustomer();
        Long attackerId = insertCustomer();
        Long accountId = insertAccount(ownerId, nextAccountNumber(), "ACTIVE");

        assertThat(adapter.belongsToCustomer(accountId, attackerId)).isFalse();
    }

    @Test
    @DisplayName("별칭이 설정된 계좌는 별칭을 반환한다")
    void findAccountAlias_set_returnsAlias() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "ACTIVE");
        entityManager
                .createNativeQuery("UPDATE account SET alias = :alias WHERE account_id = :accountId")
                .setParameter("alias", "월세계좌")
                .setParameter("accountId", accountId)
                .executeUpdate();

        assertThat(adapter.findAccountAlias(accountId)).contains("월세계좌");
    }

    @Test
    @DisplayName("별칭이 미설정이면 빈 값을 반환한다")
    void findAccountAlias_notSet_returnsEmpty() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "ACTIVE");

        assertThat(adapter.findAccountAlias(accountId)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 계좌ID는 빈 값을 반환한다")
    void findAccountAlias_notFound_returnsEmpty() {
        assertThat(adapter.findAccountAlias(999_999_999L)).isEmpty();
    }

    private String nextAccountNumber() {
        return String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAccount(Long customerId, String accountNumber, String status) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', :status, 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("status", status)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    // ck_account_maturity 제약상 예·적금(TIME_DEPOSIT·INSTALLMENT_SAVINGS)은 maturity_date가
    // opened_date 이후로 반드시 있어야 한다 - opened_date를 NOW()로 넣으므로 maturityDate는 미래여야 한다.
    // ck_account_product 제약상 같은 계좌유형은 product_id도 NOT NULL이라 최소 상품 행을 같이 시드한다
    // (TransferExecutionServiceTest의 INSTALLMENT_SAVINGS 픽스처와 동일 패턴).
    private Long insertAccount(
            Long customerId, String accountNumber, String status, String accountType, LocalDate maturityDate) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO product (product_id, product_code, product_name, product_group, deposit_type,
                            base_rate, max_rate, min_amount, max_amount, amount_unit, min_term_months, max_term_months,
                            interest_pay_type, sale_status, created_at, updated_at)
                        VALUES (9001, 'AUT-TEST-001', '테스트 정기적금', 'SAVINGS', 'INSTALLMENT',
                            2.50, 3.00, 100000, 100000000, 10000, 6, 36,
                            'SIMPLE', 'ON_SALE', NOW(), NOW())
                        ON DUPLICATE KEY UPDATE product_id = product_id
                        """)
                .executeUpdate();
        entityManager
                .createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, product_id, account_type, status, password_hash, opened_date, maturity_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 9001, :accountType, :status, 'x', NOW(), :maturityDate, NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("accountType", accountType)
                .setParameter("status", status)
                .setParameter("maturityDate", maturityDate)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }
}
