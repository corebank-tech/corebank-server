package com.shinhan.corebank.transfer.api;

/**
 * 보류한 이체를 실행·거절·취소하는 계약(#771). 구현은 #772다.
 *
 * 상태 전이는 조건부 UPDATE라 같은 보류 건을 두 곳에서 동시에 처리해도 한 곳만 성공한다.
 * 관리자 권한과 콜 기록 확인은 호출자가 부르기 전에 한다. 여기서는 actorId와 사유만 남긴다.
 */
public interface TransferHoldUseCase {

    /**
     * 원본 요청 그대로 한 번 실행한다. 다시 부르면 앞선 결과를 돌려준다. 토큰은 받지 않는다(인증은 보류 시점에 끝났다).
     *
     * 잔액·한도·계좌 상태와 훅 A는 실행 시점 기준으로 다시 검사하고, 거부되면 ERROR로 끝난다. 자동 재시도는 없다.
     * 훅 C는 다시 지나지 않는다. 승인하면 지연이체 설정이 함께 해당해도 바로 실행한다.
     * ON_APPROVAL 건은 보류한 영업일 안에 승인하지 않으면 만료돼 실행할 수 없다.
     */
    TransferHoldExecutionResult release(long transferHoldId, long actorId);

    /** 관리자가 거절한다. 실행하지 않고 끝낸다. */
    void reject(long transferHoldId, long actorId, String reasonCode);

    /** 고객이 취소한다. 본인 보류 건만 취소할 수 있다. */
    void cancel(long transferHoldId, long customerId);
}
