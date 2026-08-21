package com.shinhan.corebank.subscription.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * product 테이블을 겨냥한 subscription 도메인 전용 경량 매핑(transfer 모듈의
 * AccountLockJpaEntity와 동일한 패턴). 1인1계좌 제한(PRD0301) 판정 중 product_id 행에
 * 비관적 락을 걸기 위한 용도로만 쓰며, product_id 외 다른 컬럼은 다루지 않는다.
 * product 패키지를 import하지 않는다. 조회/락 전용이라 값을 바꾸지 않으므로 BaseEntity(감사
 * 컬럼)도 상속하지 않는다 — AccountLockJpaEntity와 같은 이유(어댑터 책임 범위 밖의 쓰기 방지).
 */
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLockJpaEntity {

    @Id
    private Long productId;
}
