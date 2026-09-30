package com.shinhan.corebank.transfer.api;

/**
 * 이체 확장점 훅 A — 계좌 락을 잡기 전에 이체를 거부할 기회를 다른 도메인에 연다(PH-99).
 *
 * 구현체를 빈으로 등록하기만 하면 {@code TransferExecutionService}가 {@link #order()} 오름차순으로 호출한다.
 * 한도 적립·OTP 소비보다 먼저 돌기 때문에, 여기서 거부된 요청은 한도와 OTP를 소모하지 않는다.
 * 단 계좌비밀번호 토큰은 그 전 단계에서 이미 소비되므로, 거부된 즉시이체를 재시도하려면 비밀번호를 다시 인증해야 한다.
 *
 * 락 없이 읽기만 한다. 최종 판정은 락 이후 재검증이 한다.
 */
public interface TransferPreCheck {

    /** 작을수록 먼저 실행한다. */
    int order();

    /** 거부하려면 {@code BusinessException}을 던진다. 그 이체는 ERROR로 확정된다. */
    void check(TransferPreCheckContext context);
}
