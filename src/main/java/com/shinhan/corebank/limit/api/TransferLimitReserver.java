package com.shinhan.corebank.limit.api;

/**
 * 이체 실행 시점의 한도 검사와 사용액 적립을 다른 업무 모듈에 공개한다.
 *
 * <p>모듈 간 직접 참조 금지(REQ-NFR-014)에 따라 소비자는 자기 모듈의 포트를 그대로 두고
 * 어댑터에서 이 인터페이스로 위임한다.
 */
public interface TransferLimitReserver {

    /**
     * 1회·1일 이체한도를 검사하고 당일 사용액에 적립한다(REQ-TRSF-010·011).
     *
     * <p>검사와 적립을 한 트랜잭션 안에서 원자적으로 처리하려고 두 동작을 한 메서드에 묶었다.
     * 호출자의 트랜잭션에 참여하므로 이체가 실패하면 적립도 함께 롤백된다.
     *
     * <p>1회 한도 초과는 LMT0002, 1일 한도 초과는 LMT0003 을 던진다. 호출자가 반환값을 보고
     * 판단하는 게 아니라 이 메서드가 스스로 던진다.
     *
     * @param customerId 세션에서 얻은 고객 식별자. 클라이언트가 보낸 값을 신뢰하지 않는다(REQ-NFR-007)
     * @param amount     이번에 이체할 금액
     */
    void checkAndReserve(Long customerId, long amount);
}
