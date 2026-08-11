package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * account 테이블을 겨냥한 transfer 도메인 전용 경량 매핑.
 * account 모듈의 AccountJpaEntity와는 별개이며, 이체 실행 중 계좌 락·잔액 변경에
 * 필요한 컬럼(account_id, balance, status)만 매핑한다. account 패키지를 import하지 않는다.
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AccountLockJpaEntity {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    /**
     * account.version은 P2(계좌 도메인)의 낙관적 락 컬럼이다. 이체 실행 경로는 비관적 락으로
     * 진행 중 동시 접근을 막지만, 락 반납 이후 이 행의 과거 스냅샷을 들고 있던 다른 경로(P2)가
     * 뒤늦게 그 스냅샷을 통째로 저장하면 우리가 반영한 변경이 조용히 덮어써질 수 있다(lost update).
     * 우리도 갱신 시 version을 증가시켜, 그런 지연 저장 시도가 낙관적 락 충돌로 감지되게 한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public void debit(long amount) {
        this.balance -= amount;
    }

    public void credit(long amount) {
        this.balance += amount;
    }
}
