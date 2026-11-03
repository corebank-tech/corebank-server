package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * 즉시이체 한 건을 보류했다. 보류 저장과 같은 커밋에 실린다(ADR-0010). 보류 뒤 할 일(조사·알림)은 이 이벤트를 받아서 한다.
 *
 * 업무 결과가 확정된 게 아니라 DomainEvent를 구현하지 않는다. 결과는 실행될 때 TransferSettled로 나간다.
 * 계좌번호·인증 토큰은 넣지 않는다. 필요하면 transferHoldId로 조회한다.
 */
public record TransferHeld(
        long transferHoldId,
        long customerId,
        String policyCode,
        TransferHoldReleaseMode releaseMode,
        LocalDateTime releaseAt,
        String flowId,
        LocalDateTime heldAt) {}
