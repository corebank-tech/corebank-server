package com.shinhan.corebank.transfer.application.port.in;

/**
 * 정정 체인 유스케이스 (PH-80). 이미 성공한 이체를 지우지 않고 바로잡는다.
 *
 * 취소정정(reverse)은 원거래를 무효화하고 반대 방향 거래로 돈을 되돌린다. 정상거래(repost)는 취소정정이 끝난
 * 원거래에 대해 원래 의도한 거래를 같은 두 계좌로 새로 기표한다. 둘 다 원거래를 가리키고, 원거래 하나에
 * 각각 한 번만 허용된다(transfer.uk_transfer_correction). 호출자는 PH-33-② 타행 조회거래·PH-36 보상이며
 * HTTP 경로는 없다.
 */
public interface TransferCorrectionUseCase {

    TransferReversalResult reverse(String originalTransactionNumber);

    TransferRepostResult repost(String originalTransactionNumber, long correctedAmount);

    record TransferReversalResult(String reversalTransactionNumber, String originalTransactionNumber, long amount) {}

    record TransferRepostResult(String repostTransactionNumber, String originalTransactionNumber, long amount) {}
}
