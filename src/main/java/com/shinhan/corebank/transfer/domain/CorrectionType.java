package com.shinhan.corebank.transfer.domain;

// 정정 체인에서 원거래에 딸린 거래의 역할. 일반 이체는 null이다(transfer.correction_type).
public enum CorrectionType {
    REVERSAL, // 취소정정 — 원거래와 반대 방향으로 돈을 되돌린다
    REPOST // 정상거래 — 원래 의도한 거래를 새로 기표한다
}
