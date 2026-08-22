package com.shinhan.corebank.limit.api;

/**
 * 고객의 1회 이체한도를 다른 업무 모듈에 공개한다. 자동이체·예약이체는 등록 시점에 금액이
 * 1회 한도를 넘는지만 보므로 값만 넘겨받아 각자 판단한다.
 *
 * <p>TransferLimitReserver 와 나눈 이유는 소비자가 서로 다르기 때문이다. 이체 실행은 적립까지
 * 필요하고, 등록 검증은 읽기만 하면 된다. 둘 다 쓰는 소비자는 없다.
 */
public interface TransferLimitProvider {

    /** 고객의 1회 이체한도. 한도 행이 없으면 정책 기본값(POL-013)을 돌려준다. 락을 걸지 않는다. */
    long findOneTimeLimit(Long customerId);
}
