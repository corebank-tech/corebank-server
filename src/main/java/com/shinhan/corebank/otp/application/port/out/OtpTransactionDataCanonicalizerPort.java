package com.shinhan.corebank.otp.application.port.out;

import java.util.Map;

// 거래 JSON의 Key와 숫자 표현을 정규화한 비교용 문자열을 생성한다.
public interface OtpTransactionDataCanonicalizerPort {
    String canonicalize(Map<String, Object> transactionData);

    Map<String, Object> parse(String canonicalTransactionData);
}
