package com.shinhan.corebank.transfer.application.port.in;

/**
 * 정정 체인 취소정정 유스케이스 (PH-80).
 *
 * 이미 성공한 이체를 지우지 않고 되돌린다 — 원거래를 무효화하고, 반대 방향 취소정정 이체와
 * 원장 반대기표를 새로 쌓는다. 호출자는 PH-33-② 타행 조회거래·PH-36 보상이며 HTTP 경로는 없다.
 * 같은 원거래는 한 번만 되돌릴 수 있다(transfer.uk_transfer_correction).
 */
public interface TransferReversalUseCase {

    TransferReversalResult reverse(String originalTransactionNumber);

    record TransferReversalResult(String reversalTransactionNumber, String originalTransactionNumber, long amount) {}
}
