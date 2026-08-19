package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
import com.shinhan.corebank.transfer.application.port.out.WithdrawalAccountDetail;
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
    @DisplayName("계좌번호로 계좌 ID와 예금주명을 조회한다")
    void resolvePayeeByAccountNumber_returnsAccountIdAndPayeeName() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ResolvedPayee> resolved = adapter.resolvePayeeByAccountNumber("110222222222");

        // then
        assertThat(resolved).contains(
                new ResolvedPayee(202L, "테스터", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE));
    }

    @Test
    @DisplayName("입금계좌가 정지 상태이면 상태값도 함께 조회된다")
    void resolvePayeeByAccountNumber_returnsSuspendedStatus() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.createNativeQuery("UPDATE account SET status = 'SUSPENDED' WHERE account_id = 202")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ResolvedPayee> resolved = adapter.resolvePayeeByAccountNumber("110222222222");

        // then
        assertThat(resolved).contains(
                new ResolvedPayee(202L, "테스터", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.SUSPENDED));
    }

    @Test
    @DisplayName("존재하지 않는 계좌번호는 빈 Optional을 반환한다")
    void resolvePayeeByAccountNumber_returnsEmpty_whenAccountNumberNotFound() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ResolvedPayee> resolved = adapter.resolvePayeeByAccountNumber("999999999999");

        // then
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("출금계좌 ID로 소유자·출금계좌 등록 여부를 조회한다")
    void findWithdrawalAccountDetail_returnsCustomerIdAndRegistrationStatus() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<WithdrawalAccountDetail> detail = adapter.findWithdrawalAccountDetail(101L);

        // then
        assertThat(detail).contains(new WithdrawalAccountDetail(101L, 1L, true));
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않은 계좌는 withdrawalRegistered=false로 조회된다")
    void findWithdrawalAccountDetail_returnsFalse_whenNotRegisteredAsWithdrawalAccount() {
        // given: 202는 픽스처에서 출금계좌로 등록되지 않음
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<WithdrawalAccountDetail> detail = adapter.findWithdrawalAccountDetail(202L);

        // then
        assertThat(detail).contains(new WithdrawalAccountDetail(202L, 1L, false));
    }

    @Test
    @DisplayName("존재하지 않는 계좌 ID로 조회하면 빈 Optional을 반환한다")
    void findWithdrawalAccountDetail_returnsEmpty_whenAccountNotFound() {
        // when
        Optional<WithdrawalAccountDetail> detail = adapter.findWithdrawalAccountDetail(999999L);

        // then
        assertThat(detail).isEmpty();
    }

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
        assertThat(locked.withdrawal().accountNumber()).isEqualTo("110111111111");
        assertThat(locked.withdrawal().balance()).isEqualTo(100000L);
        assertThat(locked.deposit().accountId()).isEqualTo(202L);
        assertThat(locked.deposit().accountNumber()).isEqualTo("110222222222");
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
    @DisplayName("applyTransfer 호출 후 커밋 시점에 출금/입금 계좌 잔액이 반영되고 변경 후 잔액을 반환한다")
    void applyTransfer_updatesBothBalances_andReturnsBalancesAfter() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        LockedAccountsForTransfer locked = adapter.lockForTransfer(101L, 202L);

        // when
        TransferBalances balances = adapter.applyTransfer(locked, 30000L, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(balances.withdrawalBalanceAfter()).isEqualTo(70000L);
        assertThat(balances.depositBalanceAfter()).isEqualTo(130000L);

        long withdrawalBalance = ((Number) entityManager
                .createNativeQuery("SELECT balance FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();
        long depositBalance = ((Number) entityManager
                .createNativeQuery("SELECT balance FROM account WHERE account_id = 202")
                .getSingleResult())
                .longValue();
        assertThat(withdrawalBalance).isEqualTo(70000L);
        assertThat(depositBalance).isEqualTo(130000L);
    }

    @Test
    @DisplayName("applyTransfer 호출 시 전달한 executedAt으로 출금/입금 계좌의 last_transaction_at이 갱신된다")
    void applyTransfer_updatesLastTransactionAt_forBothAccounts() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        LockedAccountsForTransfer locked = adapter.lockForTransfer(101L, 202L);
        LocalDateTime executedAt = LocalDateTime.of(2026, 8, 18, 10, 30, 0);

        // when
        adapter.applyTransfer(locked, 30000L, executedAt);
        entityManager.flush();
        entityManager.clear();

        // then
        LocalDateTime withdrawalLastTransactionAt = (LocalDateTime) entityManager
                .createNativeQuery("SELECT last_transaction_at FROM account WHERE account_id = 101")
                .getSingleResult();
        LocalDateTime depositLastTransactionAt = (LocalDateTime) entityManager
                .createNativeQuery("SELECT last_transaction_at FROM account WHERE account_id = 202")
                .getSingleResult();
        assertThat(withdrawalLastTransactionAt).isEqualTo(executedAt);
        assertThat(depositLastTransactionAt).isEqualTo(executedAt);
    }

    @Test
    @DisplayName("applyTransfer 이후 출금/입금 계좌 모두 version이 1씩 증가한다")
    void applyTransfer_incrementsVersionForBothAccounts() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        LockedAccountsForTransfer locked = adapter.lockForTransfer(101L, 202L);

        long withdrawalVersionBefore = ((Number) entityManager
                .createNativeQuery("SELECT version FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();
        long depositVersionBefore = ((Number) entityManager
                .createNativeQuery("SELECT version FROM account WHERE account_id = 202")
                .getSingleResult())
                .longValue();

        // when
        adapter.applyTransfer(locked, 30000L, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        // then
        long withdrawalVersionAfter = ((Number) entityManager
                .createNativeQuery("SELECT version FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();
        long depositVersionAfter = ((Number) entityManager
                .createNativeQuery("SELECT version FROM account WHERE account_id = 202")
                .getSingleResult())
                .longValue();
        assertThat(withdrawalVersionAfter).isEqualTo(withdrawalVersionBefore + 1);
        assertThat(depositVersionAfter).isEqualTo(depositVersionBefore + 1);
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
