package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AccountLockPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private AccountLockPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("출금/입금 계좌를 락으로 획득하면 각 계좌의 현재 잔액이 방향에 맞게 반환된다")
    void lockForTransfer_returnsWithdrawalAndDepositSnapshots() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        LockedAccountsForTransfer locked = adapter.lockForTransfer(101L, 202L);

        // then
        assertThat(locked.withdrawal().accountId()).isEqualTo(101L);
        assertThat(locked.withdrawal().balance()).isEqualTo(100000L);
        assertThat(locked.deposit().accountId()).isEqualTo(202L);
        assertThat(locked.deposit().balance()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("출금/입금 계좌 ID 순서를 반대로 호출해도 방향 매핑은 그대로 유지된다")
    void lockForTransfer_preservesDirectionRegardlessOfIdOrder() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when: 입금계좌(202)가 출금 역할, 출금계좌(101)가 입금 역할인 이체
        LockedAccountsForTransfer locked = adapter.lockForTransfer(202L, 101L);

        // then
        assertThat(locked.withdrawal().accountId()).isEqualTo(202L);
        assertThat(locked.deposit().accountId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("debit 호출 후 커밋 시점에 계좌 잔액이 차감된 값으로 반영된다")
    void debit_decreasesBalance() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        adapter.debit(101L, 30000L);
        entityManager.flush();
        entityManager.clear();

        // then
        long balance = ((Number) entityManager
                .createNativeQuery("SELECT balance FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();
        assertThat(balance).isEqualTo(70000L);
    }

    @Test
    @DisplayName("credit 호출 후 커밋 시점에 계좌 잔액이 증가된 값으로 반영된다")
    void credit_increasesBalance() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        adapter.credit(202L, 30000L);
        entityManager.flush();
        entityManager.clear();

        // then
        long balance = ((Number) entityManager
                .createNativeQuery("SELECT balance FROM account WHERE account_id = 202")
                .getSingleResult())
                .longValue();
        assertThat(balance).isEqualTo(130000L);
    }

    @Test
    @DisplayName("debit 이후 계좌의 version이 1 증가한다")
    void debit_incrementsVersion() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        long versionBefore = ((Number) entityManager
                .createNativeQuery("SELECT version FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();

        // when
        adapter.debit(101L, 30000L);
        entityManager.flush();
        entityManager.clear();

        // then
        long versionAfter = ((Number) entityManager
                .createNativeQuery("SELECT version FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();
        assertThat(versionAfter).isEqualTo(versionBefore + 1);
    }

    @Test
    @DisplayName("존재하지 않는 계좌로 락을 시도하면 BusinessException(TRF9001)이 발생한다")
    void lockForTransfer_throwsBusinessException_whenAccountNotFound() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> adapter.lockForTransfer(101L, 999999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TransferErrorCode.ACCOUNT_LOCK_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("출금계좌 ID가 null이면 BusinessException(CMN0002)을 던진다")
    void lockForTransfer_throwsBusinessException_whenWithdrawalAccountIdIsNull() {
        assertThatThrownBy(() -> adapter.lockForTransfer(null, 202L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("입금계좌 ID가 null이면 BusinessException(CMN0002)을 던진다")
    void lockForTransfer_throwsBusinessException_whenDepositAccountIdIsNull() {
        assertThatThrownBy(() -> adapter.lockForTransfer(101L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("출금/입금 계좌가 동일하면 BusinessException(TRF0002)을 던진다")
    void lockForTransfer_throwsBusinessException_whenSameAccount() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> adapter.lockForTransfer(101L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
    }
}
