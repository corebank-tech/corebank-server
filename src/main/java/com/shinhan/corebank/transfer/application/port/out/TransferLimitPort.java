package com.shinhan.corebank.transfer.application.port.out;

/**
 * transfer 모듈 전용 한도 검증 포트. autotransfer.TransferLimitPort와 이름은 같지만
 * 모듈 간 참조 금지 원칙에 따라 별도 인터페이스다.
 *
 * <p>1회/1일 한도 위반 시 이 메서드 스스로 BusinessException(LimitErrorCode)을 던진다 —
 * 호출자가 반환값을 비교해 예외를 던지는 게 아니다. 실제 한도 도메인(P1)이 들어오면 1일 누적
 * 사용량을 같은 트랜잭션 안에서 원자적으로 검사+적립(reserve)해야 하므로, 그 원자적 동작을
 * 포트 구현체 내부로 캡슐화해 둔다.
 */
public interface TransferLimitPort {
    void checkAndReserve(Long customerId, long amount);
}
