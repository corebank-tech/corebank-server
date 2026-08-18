package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 데드락 회피의 핵심 메커니즘(항상 계좌 ID 오름차순으로 락을 건다)을 실제 스레드 타이밍에
 * 의존하지 않고 결정적으로 검증한다. 실제 MySQL 데드락 미발생 여부는
 * AccountLockDeadlockAvoidanceTest(통합 테스트)가 별도로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLockPersistenceAdapter 락 획득 순서 단위 테스트")
class AccountLockPersistenceAdapterOrderingTest {

    @Mock
    private AccountLockJpaRepository repository;

    @InjectMocks
    private AccountLockPersistenceAdapter adapter;

    @Test
    @DisplayName("출금계좌 ID가 입금계좌 ID보다 커도 작은 ID부터 락을 조회한다")
    void lockForTransfer_locksAscendingIdOrder_whenWithdrawalIdIsLarger() {
        // given
        when(repository.findByAccountIdForUpdate(101L)).thenReturn(Optional.of(entity(101L)));
        when(repository.findByAccountIdForUpdate(202L)).thenReturn(Optional.of(entity(202L)));

        // when: 출금(202)이 입금(101)보다 ID가 큼 — 역할과 ID 크기가 반대
        adapter.lockForTransfer(202L, 101L);

        // then: 그래도 항상 작은 ID(101)부터 조회한다
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).findByAccountIdForUpdate(101L);
        inOrder.verify(repository).findByAccountIdForUpdate(202L);
    }

    @Test
    @DisplayName("출금계좌 ID가 입금계좌 ID보다 작아도 작은 ID부터 락을 조회한다")
    void lockForTransfer_locksAscendingIdOrder_whenWithdrawalIdIsSmaller() {
        // given
        when(repository.findByAccountIdForUpdate(101L)).thenReturn(Optional.of(entity(101L)));
        when(repository.findByAccountIdForUpdate(202L)).thenReturn(Optional.of(entity(202L)));

        // when: 출금(101)이 입금(202)보다 ID가 작음 — 역할과 ID 크기가 같은 방향
        adapter.lockForTransfer(101L, 202L);

        // then: 여전히 작은 ID(101)부터 조회한다
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).findByAccountIdForUpdate(101L);
        inOrder.verify(repository).findByAccountIdForUpdate(202L);
    }

    private AccountLockJpaEntity entity(long accountId) {
        return AccountLockJpaEntity.builder()
                .accountId(accountId)
                .balance(100_000L)
                .status("ACTIVE")
                .build();
    }
}
