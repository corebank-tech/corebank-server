package com.shinhan.corebank.paymentstop.domain.gate;

import java.util.List;

/**
 * 게이트 입력. 원장 사실과 접수 정보만 담는다. 소명 서술, OCR 텍스트, Agent 판정안은 근거가 아니라 넣지 않는다(D9).
 *
 * @param disputedTransfers 피해 신고된 다툼 거래. 지급정지 계좌의 입금이다
 * @param accountTransfers 지급정지 계좌의 거래 전체(다툼 거래 포함). 근거 제외는 게이트가 한다
 * @param senderAccountId 다툼 거래 송금인의 당행 계좌 ID. 타행 송금이면 null이라 관계 인정을 할 수 없다
 * @param otherStopHistory 같은 계좌의 다른 사건 지급정지 이력(해제 포함)
 * @param simplifiedRequested 명의인이 간소화(일부 지급정지) 절차를 신청했는지
 * @param clause 이의제기 사유 조항(법 제7조제1항 1~3호)
 * @param litigationPending 명의인과 피해자 사이 소송 계속 여부(법 제8조제2항제1호의2)
 * @param externalReplies 외부 확인 회신. 원장 밖 사실이지만 기관 회신이라 근거로 쓴다(순서 1, 4)
 */
public record GateFacts(
        List<LedgerFact> disputedTransfers,
        List<LedgerFact> accountTransfers,
        Long senderAccountId,
        boolean otherStopHistory,
        boolean simplifiedRequested,
        int clause,
        boolean litigationPending,
        List<ExternalReply> externalReplies) {

    public GateFacts {
        if (disputedTransfers == null || disputedTransfers.isEmpty()) {
            throw new IllegalArgumentException("다툼 거래가 하나 이상 있어야 합니다.");
        }
        if (disputedTransfers.stream().anyMatch(fact -> !fact.incoming())) {
            throw new IllegalArgumentException("다툼 거래는 지급정지 계좌의 입금이어야 합니다.");
        }
        if (clause < 1 || clause > 3) {
            throw new IllegalArgumentException("사유 조항은 1~3호입니다.");
        }
        disputedTransfers = List.copyOf(disputedTransfers);
        accountTransfers = accountTransfers == null ? List.of() : List.copyOf(accountTransfers);
        externalReplies = externalReplies == null ? List.of() : List.copyOf(externalReplies);
    }
}
