package com.shinhan.corebank.otp.adapter.out.security;

import com.shinhan.corebank.otp.application.port.out.OtpCodeHashPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// OTP 평문을 BCrypt로 해시하고 사용자가 입력한 번호와 비교한다.
@Component
@ConditionalOnProperty(name = "app.otp.code-hash.algorithm", havingValue = "bcrypt", matchIfMissing = true)
public class BCryptOtpCodeHashAdapter implements OtpCodeHashPort {

    private final PasswordEncoder passwordEncoder;

    public BCryptOtpCodeHashAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String otpCode) {
        return passwordEncoder.encode(otpCode);
    }

    @Override
    public boolean matches(String otpCode, String otpCodeHash) {
        return passwordEncoder.matches(otpCode, otpCodeHash);
    }
}
