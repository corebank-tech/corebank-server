package com.shinhan.corebank.gl.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회계 원장(GL) 도메인 오류코드. 값은 docs/api_conventions.md §4-14 마스터를 따른다.
 *
 * <p>전표는 사용자 입력이 아니라 이체·상품가입·이자 같은 내부 거래에서 만들어진다. 그래서 전부
 * 9xxx(500)다 — 여기서 걸리면 호출한 쪽의 분개 패턴이 틀린 것이다.
 */
@Getter
@RequiredArgsConstructor
public enum GlErrorCode implements ErrorCode {
    UNBALANCED_VOUCHER("GLA9001", 500, "전표의 차변 합계와 대변 합계가 다릅니다."),
    TOO_FEW_JOURNAL_ENTRIES("GLA9002", 500, "전표에는 분개가 2줄 이상 있어야 합니다."),
    NON_POSITIVE_JOURNAL_AMOUNT("GLA9003", 500, "분개 금액은 0보다 커야 합니다."),
    OPENING_CASH_BELOW_DEPOSITS("GLA9004", 500, "개시 현금성 총액이 고객 예수금 합계보다 작습니다."),
    VOUCHER_SEQUENCE_EXHAUSTED("GLA9005", 500, "전표번호 일련번호 채번 가능 범위를 초과했습니다."),
    VOUCHER_TYPE_MISMATCH("GLA9006", 500, "전표번호의 유형이 전표 유형과 다릅니다."),
    MISSING_REFERENCE_KEY("GLA9007", 500, "전표의 참조 키가 없습니다.");

    private final String code;
    private final int status;
    private final String message;
}
