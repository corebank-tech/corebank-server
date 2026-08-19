package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * customer 테이블을 겨냥한 transfer 도메인 전용 경량 매핑. payee_name 스냅샷을 만들기 위해
 * account.customer_id로 예금주명(user_name)만 조회하는 용도이며, AccountLockJpaEntity와
 * 동일하게 customer 모듈을 import하지 않고 이 패키지 안에서 부분 매핑한다.
 */
@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerNameJpaEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "user_name", nullable = false, length = 50, insertable = false, updatable = false)
    private String userName;
}
