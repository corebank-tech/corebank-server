package com.shinhan.corebank.autotransfer.adapter.out.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// account 테이블 겨냥한 autotransfer 도메인 전용 경량 매핑 (조회 전용). account 패키지를 import하지 않는다.
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountLookupJpaEntity {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_number", nullable = false, length = 12, insertable = false, updatable = false)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false, insertable = false, updatable = false)
    private Long customerId;

    @Column(name = "account_type", nullable = false, length = 24, insertable = false, updatable = false)
    private String accountType;

    @Column(name = "status", nullable = false, length = 12, insertable = false, updatable = false)
    private String status;

    @Column(name = "alias", length = 24, insertable = false, updatable = false)
    private String alias;
}
