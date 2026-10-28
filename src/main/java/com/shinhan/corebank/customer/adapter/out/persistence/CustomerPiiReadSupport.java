package com.shinhan.corebank.customer.adapter.out.persistence;

import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.EMAIL;
import static com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCipher.Field.PHONE_NUMBER;

import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCrypto;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CustomerPiiReadSupport {

    private final boolean encryptedReads;
    private final ObjectProvider<CustomerPiiCrypto> cryptoProvider;

    public CustomerPiiReadSupport(
            @Value("${app.pii.read-encrypted:false}") boolean encryptedReads,
            ObjectProvider<CustomerPiiCrypto> cryptoProvider) {
        this.encryptedReads = encryptedReads;
        this.cryptoProvider = cryptoProvider;
    }

    @PostConstruct
    void validateConfiguration() {
        if (encryptedReads && cryptoProvider.getIfAvailable() == null) {
            throw new IllegalStateException("암호화 조회를 사용하려면 app.pii.enabled=true와 키 설정이 필요합니다.");
        }
    }

    public boolean encryptedReads() {
        return encryptedReads;
    }

    // 인자로 전달된 값은 현재 조회 모드에서 선택한 DB 컬럼 값이다.
    public String email(String storedValue) {
        if (!encryptedReads) {
            return storedValue;
        }
        if (storedValue == null) {
            throw new IllegalStateException("고객 이메일 암호문이 누락되었습니다.");
        }

        return crypto().cipher().decrypt(storedValue, EMAIL);
    }

    public String phoneNumber(String storedValue) {
        if (!encryptedReads) {
            return storedValue;
        }
        if (storedValue == null) {
            throw new IllegalStateException("고객 휴대폰 암호문이 누락되었습니다.");
        }

        return crypto().cipher().decrypt(storedValue, PHONE_NUMBER);
    }

    public String emailHash(String email) {
        if (!encryptedReads) {
            throw new IllegalStateException("이메일 해시 검색은 암호화 조회 모드에서만 사용합니다.");
        }
        Objects.requireNonNull(email, "email must not be null");

        return crypto().emailBlindIndex().hash(email);
    }

    private CustomerPiiCrypto crypto() {
        CustomerPiiCrypto crypto = cryptoProvider.getIfAvailable();
        if (crypto == null) {
            throw new IllegalStateException("개인정보 암호화 설정이 누락되었습니다.");
        }
        return crypto;
    }
}
