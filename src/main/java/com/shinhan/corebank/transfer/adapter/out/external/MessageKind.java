package com.shinhan.corebank.transfer.adapter.out.external;

import java.util.Arrays;

/** 전문종별 (규격 §2 헤더 2번). */
public enum MessageKind {
    REQUEST("0200"),
    RESPONSE("0210");

    private final String code;

    MessageKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    static MessageKind fromCode(String code) {
        return Arrays.stream(values())
                .filter(kind -> kind.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 전문종별이다"));
    }
}
