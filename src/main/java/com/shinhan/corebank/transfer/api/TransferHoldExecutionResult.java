package com.shinhan.corebank.transfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;

/**
 * 보류 건 실행 결과.
 *
 * @param status            SUCCESS 또는 ERROR
 * @param transactionNumber 실행한 이체의 거래번호. 이체 행이 생기기 전에 거부됐으면 null
 * @param errorCode         실패 사유 코드. 성공이면 null
 */
public record TransferHoldExecutionResult(ProcessResultStatus status, String transactionNumber, String errorCode) {}
