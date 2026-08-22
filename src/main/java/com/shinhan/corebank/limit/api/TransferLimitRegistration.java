package com.shinhan.corebank.limit.api;

/**
 * 신규 가입 고객에게 이체한도를 부여하는 기능을 회원가입 모듈에 공개한다.
 *
 * <p>모듈 간 직접 참조 금지(REQ-NFR-014)와 ADR-0002 에 따라, signup 이 transfer_limit 을 직접
 * 매핑하지 않고 소유 도메인인 limit 을 경유한다. 기본값이 무엇인지, 이미 행이 있으면 어떻게
 * 하는지를 판정하는 규칙이 limit 한 곳에만 남는다.
 */
public interface TransferLimitRegistration {

    /**
     * 정책 기본값 이체한도를 부여한다(REQ-TRSF-029). 1회 100만 / 1일 500만이다(POL-013·014).
     *
     * <p>호출자의 트랜잭션에 참여한다. 가입이 롤백되면 한도도 함께 롤백되므로 "고객은 있는데
     * 한도가 없는" 상태가 생기지 않는다.
     *
     * @param customerId 가입 트랜잭션에서 채번된 고객 식별자
     */
    void registerDefault(Long customerId);
}
