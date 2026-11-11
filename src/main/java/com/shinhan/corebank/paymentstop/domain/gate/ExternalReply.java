package com.shinhan.corebank.paymentstop.domain.gate;

/**
 * 외부 확인 회신(수사기관, 관계 금융회사). 부정 회신은 불수용 후보(순서 1), 명의인 무관은 수용 후보(순서 4)로 이어진다. 회신 요청과
 * 저장은 PA-10(모의)에서 붙고, 그 전에는 게이트 입력이 비어 있다.
 */
public enum ExternalReply {
    // 수사기관: 명의인의 사기 가담 의심
    INVOLVEMENT_SUSPECTED(true, "가담 의심"),
    // 제출 서류(공문서)가 위조로 판명
    DOCUMENT_FORGED(true, "서류 위조 판명"),
    // 수사기관: 명의인은 사기와 무관
    OWNER_UNRELATED(false, "명의인 무관");

    private final boolean negative;
    private final String label;

    ExternalReply(boolean negative, String label) {
        this.negative = negative;
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean negative() {
        return negative;
    }
}
