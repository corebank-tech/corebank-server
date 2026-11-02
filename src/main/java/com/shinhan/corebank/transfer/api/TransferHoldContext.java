package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * 훅 C 입력. api는 application 타입을 참조할 수 없어 TransferCommand 대신 필요한 값만 담는다.
 *
 * @param customerId           요청한 고객
 * @param withdrawalAccountId  출금계좌 ID
 * @param depositAccountNumber 수취계좌번호(당행, 숫자만)
 * @param amount               이체 금액(원)
 * @param requestedAt          채번 시각. 시간 창의 기준으로 쓴다
 * @param flowId               FE 행동 흐름 식별자. 보내지 않았으면 null
 */
public record TransferHoldContext(
        long customerId,
        long withdrawalAccountId,
        String depositAccountNumber,
        long amount,
        LocalDateTime requestedAt,
        String flowId) {}
