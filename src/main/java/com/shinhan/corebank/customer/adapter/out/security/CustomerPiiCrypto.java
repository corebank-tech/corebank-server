package com.shinhan.corebank.customer.adapter.out.security;

import java.util.Objects;

// PH-18: 저장용 암복호화와 정확 검색용 해시를 분리해 제공한다.
// 고객 DB에 연결하는 단계가 준비될 때까지 별도의 빈으로만 생성된다.
public record CustomerPiiCrypto(CustomerPiiCipher cipher, CustomerEmailBlindIndex emailBlindIndex) {

    public CustomerPiiCrypto {
        Objects.requireNonNull(cipher, "cipher must not be null");
        Objects.requireNonNull(emailBlindIndex, "emailBlindIndex must not be null");
    }
}
