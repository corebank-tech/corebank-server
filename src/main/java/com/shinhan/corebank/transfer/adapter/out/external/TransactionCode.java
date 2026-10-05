package com.shinhan.corebank.transfer.adapter.out.external;

import java.util.Arrays;

/** 거래코드 (규격 §2 헤더 3번). */
public enum TransactionCode {
    TRANSFER("TRF001"),
    INQUIRY("INQ001");

    private final String code;

    TransactionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    static TransactionCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(transactionCode -> transactionCode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 거래코드다"));
    }
}
