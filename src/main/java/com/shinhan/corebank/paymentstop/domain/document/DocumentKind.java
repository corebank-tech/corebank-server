package com.shinhan.corebank.paymentstop.domain.document;

public enum DocumentKind {
    // 증빙. Agent 가 OCR 로 읽는다
    EVIDENCE,
    // 신분증 사본. 암호화 저장하고 Agent 에게는 존재 여부만 알린다(D37)
    ID_COPY
}
