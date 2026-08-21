package com.shinhan.corebank.subscription.adapter.out.account;


import com.shinhan.corebank.account.domain.AccountStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// account 테이블을 겨냥한 subscription 도메인 전용 부분 매핑(읽기 전용).
// account 모듈의 AccountJpaEntity/AccountJpaRepository는 import하지 않는다 — transfer 모듈의
// AccountLockJpaEntity와 동일한 관례. 이 어댑터는 절대 값을 쓰지 않으므로 모든 컬럼을
// insertable/updatable=false로 막는다.
//
// 계좌번호 단건 조회(#177)는 account 공개 UseCase(AccountNumberQueryUseCase)로 이관했고, 이 매핑은
// 가입 사전검증의 출금계좌 조회(AccountLookupPort) 전용으로 남아 있다. 두 경로를 어느 쪽으로
// 통일할지는 후속 이슈에서 판단한다.
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionAccountJpaEntity {
    @Id
    @Column(name = "account_id", insertable = false, updatable = false)
    private Long accountId;

    @Column(name = "account_number", insertable = false, updatable = false)
    private String accountNumber;

    @Column(name = "customer_id", insertable = false, updatable = false)
    private Long customerId;

    @Column(name = "balance", insertable = false, updatable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", insertable = false, updatable = false)
    private AccountStatus status;

    @Column(name = "withdrawal_registered", insertable = false, updatable = false)
    private boolean withdrawalRegistered;
}
