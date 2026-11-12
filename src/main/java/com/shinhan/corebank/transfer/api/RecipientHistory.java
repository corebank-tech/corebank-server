package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * 한 수취계좌로 보낸 성공 이력 요약.
 *
 * @param firstSucceededAt 고객의 모든 출금계좌에서 이 수취계좌로 보낸 가장 이른 성공 시각. 보낸 적 없으면 null
 * @param otherWithdrawalAccountCount 제외 계좌 말고 이 수취계좌로 성공 이체한 서로 다른 출금계좌 수. 이체 건수가 아니다
 */
public record RecipientHistory(LocalDateTime firstSucceededAt, int otherWithdrawalAccountCount) {}
