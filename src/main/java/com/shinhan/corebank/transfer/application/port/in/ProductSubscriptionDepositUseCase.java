package com.shinhan.corebank.transfer.application.port.in;

/**
 * 상품가입(정기예금) 초입금 기표 유스케이스.
 *
 * <p>{@link TransferExecutionUseCase}와 달리 호출자의 트랜잭션에 그대로 참여한다 —
 * 상품가입은 계좌개설·가입저장·약관동의·초입금기표가 전부 하나의 트랜잭션이어야 하고,
 * 뒤 단계가 실패하면 이미 옮긴 자금도 함께 롤백돼야 하기 때문이다. 실패를 ERROR 행으로
 * 남기기 위해 REQUIRES_NEW로 독립 커밋하는 이체 실행과는 요구사항이 정반대다.
 *
 * <p>subscription 도메인이 {@code AccountLockPort}·{@code LedgerSavePort} 같은 원장 엔진의
 * 아웃 포트를 직접 조립하지 않고 이 인 포트만 호출하도록 해서, 계좌 락 획득 순서와
 * 복식기표 불변식이 원장 엔진 안에만 존재하게 한다.
 */
public interface ProductSubscriptionDepositUseCase {

    ProductSubscriptionDepositResult deposit(ProductSubscriptionDepositCommand command);

    /**
     * @param withdrawalAccountId 가입금액을 빼갈 고객의 출금계좌
     * @param depositAccountId    상품가입으로 방금 개설된 신규 계좌
     * @param amount              초입금액(= 가입금액)
     */
    record ProductSubscriptionDepositCommand(
            Long withdrawalAccountId,
            Long depositAccountId,
            long amount
    ) {
    }

    record ProductSubscriptionDepositResult(
            String transactionNumber,
            long withdrawalBalanceAfter,
            long depositBalanceAfter
    ) {
    }
}
