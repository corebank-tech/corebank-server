package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    public void debit(long amount) {
        this.balance -= amount;
    }

    public void credit(long amount) {
        this.balance += amount;
    }
}
