package com.shinhan.corebank.scheduledtransfer.adapter.out.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
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
    @DisplayName("존재하는 계좌번호는 계좌 유형을 반환한다")
    void findAccountTypeByNumber_found_returnsType() {
        Long customerId = insertCustomer();
        String accountNumber = nextAccountNumber();
        insertAccount(customerId, accountNumber, "ACTIVE");

        assertThat(adapter.findAccountTypeByNumber(accountNumber)).contains(AccountType.DEMAND_DEPOSIT);
    }

    @Test
    @DisplayName("존재하지 않는 계좌번호는 빈 값을 반환한다")
    void findAccountTypeByNumber_notFound_returnsEmpty() {
        assertThat(adapter.findAccountTypeByNumber("999999999999")).isEmpty();
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
    @DisplayName("존재하는 계좌ID는 계좌번호를 반환한다")
    void findAccountNumberById_found_returnsAccountNumber() {
        Long customerId = insertCustomer();
        String accountNumber = nextAccountNumber();
        Long accountId = insertAccount(customerId, accountNumber, "ACTIVE");

        assertThat(adapter.findAccountNumberById(accountId)).contains(accountNumber);
    }

    @Test
    @DisplayName("존재하지 않는 계좌ID는 빈 값을 반환한다")
    void findAccountNumberById_notFound_returnsEmpty() {
        assertThat(adapter.findAccountNumberById(999_999_999L)).isEmpty();
    }

    @Test
    @DisplayName("여러 계좌ID를 한 번에 조회하면 계좌ID→계좌번호 맵을 반환한다")
    void findAccountNumbersByIds_returnsMap() {
        Long customerId = insertCustomer();
        String accountNumber1 = nextAccountNumber();
        String accountNumber2 = nextAccountNumber();
        Long accountId1 = insertAccount(customerId, accountNumber1, "ACTIVE");
        Long accountId2 = insertAccount(customerId, accountNumber2, "ACTIVE");

        Map<Long, String> result = adapter.findAccountNumbersByIds(List.of(accountId1, accountId2));

        assertThat(result).containsEntry(accountId1, accountNumber1).containsEntry(accountId2, accountNumber2);
    }

    @Test
    @DisplayName("빈 컬렉션을 넘기면 빈 맵을 반환하고 쿼리는 나가지 않는다")
    void findAccountNumbersByIds_empty_returnsEmptyMap() {
        assertThat(adapter.findAccountNumbersByIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("출금계좌로 등록된 계좌는 true를 반환한다")
    void isWithdrawalRegistered_registered_returnsTrue() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "ACTIVE", true);

        assertThat(adapter.isWithdrawalRegistered(accountId)).isTrue();
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않은 계좌는 false를 반환한다")
    void isWithdrawalRegistered_notRegistered_returnsFalse() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId, nextAccountNumber(), "ACTIVE", false);

        assertThat(adapter.isWithdrawalRegistered(accountId)).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 계좌ID는 false를 반환한다")
    void isWithdrawalRegistered_notFound_returnsFalse() {
        assertThat(adapter.isWithdrawalRegistered(999_999_999L)).isFalse();
    }

    @Test
    @DisplayName("별칭이 설정된 계좌만 결과 맵에 포함되고, 미설정 계좌는 키 자체가 빠진다")
    void findAccountAliasesByIds_excludesUnsetAlias() {
        Long customerId = insertCustomer();
        Long withAliasId = insertAccount(customerId, nextAccountNumber(), "ACTIVE", true);
        Long withoutAliasId = insertAccount(customerId, nextAccountNumber(), "ACTIVE", true);
        entityManager.createNativeQuery("UPDATE account SET alias = :alias WHERE account_id = :accountId")
                .setParameter("alias", "월세계좌")
                .setParameter("accountId", withAliasId)
                .executeUpdate();

        Map<Long, String> result = adapter.findAccountAliasesByIds(List.of(withAliasId, withoutAliasId));

        assertThat(result).containsEntry(withAliasId, "월세계좌").doesNotContainKey(withoutAliasId);
    }

    @Test
    @DisplayName("빈 컬렉션을 넘기면 빈 맵을 반환한다")
    void findAccountAliasesByIds_empty_returnsEmptyMap() {
        assertThat(adapter.findAccountAliasesByIds(List.of())).isEmpty();
    }

    private String nextAccountNumber() {
        return String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private Long insertAccount(Long customerId, String accountNumber, String status) {
        return insertAccount(customerId, accountNumber, status, true);
    }

    private Long insertAccount(Long customerId, String accountNumber, String status, boolean withdrawalRegistered) {
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', :status, 'x', :withdrawalRegistered, "
                                + "CASE WHEN :withdrawalRegistered THEN NOW() ELSE NULL END, NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("status", status)
                .setParameter("withdrawalRegistered", withdrawalRegistered)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
