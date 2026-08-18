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
 * account 모듈의 AccountJpaEntity(정식/대표 엔티티)와는 별개의 부분 매핑이며, 이체 실행 중
 * 계좌 락·잔액 변경에 필요한 컬럼(account_id, account_number, customer_id, balance, status,
 * version)만 다룬다.
 * account 패키지를 import하지 않는다.
 * BaseEntity(createdAt/updatedAt)는 의도적으로 상속하지 않는다 — 이 엔티티는 어디서도
 * 감사 필드를 읽지 않고, 상속 시 debit/credit마다 P2 소유 감사 컬럼(updated_at)에
 * 이 어댑터의 책임 범위 밖의 쓰기가 추가로 발생하기 때문이다.
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

    /**
     * 입금계좌번호 → ID 해석(§ resolvePayeeByAccountNumber)에만 쓰인다. 이 어댑터는 계좌번호를
     * 변경하지 않으므로 UPDATE SET절에서 제외한다.
     */
    @Column(name = "account_number", nullable = false, length = 12, insertable = false, updatable = false)
    private String accountNumber;

    /** 예금주명 스냅샷(payee_name) 조회를 위한 customer 조인 키. 조회 전용. */
    @Column(name = "customer_id", nullable = false, insertable = false, updatable = false)
    private Long customerId;

    @Column(name = "balance", nullable = false)
    private long balance;

    /** 상태는 조회 전용이다. 이 어댑터는 status를 변경하지 않으므로 UPDATE SET절에서 제외한다. */
    @Column(name = "status", nullable = false, length = 12, insertable = false, updatable = false)
    private String status;

    /** 출금계좌 등록 여부(TRF0001 검증용). 조회 전용. */
    @Column(name = "withdrawal_registered", nullable = false, insertable = false, updatable = false)
    private boolean withdrawalRegistered;

    /** 상품유형(TRF0004 입금계좌 검증용). 조회 전용. */
    @Column(name = "account_type", nullable = false, length = 24, insertable = false, updatable = false)
    private String accountType;

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
        this.balance = Math.subtractExact(this.balance, amount);
    }

    public void credit(long amount) {
        this.balance = Math.addExact(this.balance, amount);
    }
}
