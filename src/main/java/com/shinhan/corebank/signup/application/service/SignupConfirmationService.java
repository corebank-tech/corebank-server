package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.GetSignupConfirmationUseCase;
import com.shinhan.corebank.signup.application.port.in.SignupConfirmationResult;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import com.shinhan.corebank.signup.domain.model.SignupConfirmation;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

// tempSignupToken으로 회원가입 확인정보를 조회하고 화면 계약에 맞게 마스킹한다.
@Service
public class SignupConfirmationService
        implements GetSignupConfirmationUseCase {

    private static final DateTimeFormatter BIRTH_FORMAT =
            DateTimeFormatter.ofPattern("yy.MM.dd");

    private final TempSignupTokenPort tempTokenPort;
    private final ExistingBankCustomerProfilePort profilePort;

    public SignupConfirmationService(
            TempSignupTokenPort tempTokenPort,
            ExistingBankCustomerProfilePort profilePort
    ) {
        this.tempTokenPort = tempTokenPort;
        this.profilePort = profilePort;
    }

    @Override
    public SignupConfirmationResult getConfirmation(String tempSignupToken) {
        TempSignupTokenPayload payload = tempTokenPort.find(tempSignupToken)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.INVALID_INPUT
                ));
        ExistingBankCustomerProfile profile = profilePort.findByCustomerId(
                        payload.existingBankCustomerId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Mock 은행 고객을 찾을 수 없습니다."
                ));

        SignupConfirmation confirmation = new SignupConfirmation(
                maskName(profile.userName()),
                payload.userId(),
                profile.birthDate().format(BIRTH_FORMAT),
                maskPhone(payload.phoneNumber()),
                maskEmail(payload.email())
        );
        return SignupConfirmationResult.from(confirmation);
    }

    private String maskName(String name) {
        if (name.length() <= 1) {
            return "*";
        }
        if (name.length() == 2) {
            return name.substring(0, 1) + "*";
        }
        return name.substring(0, 1)
                + "*".repeat(name.length() - 2)
                + name.substring(name.length() - 1);
    }

    private String maskPhone(String phoneNumber) {
        return phoneNumber.substring(0, 3)
                + "-****-"
                + phoneNumber.substring(7);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int visibleLength = Math.min(3, Math.max(0, local.length() - 1));
        int maskedLength = local.length() - visibleLength;

        return local.substring(0, visibleLength)
                + "*".repeat(maskedLength)
                + domain;
    }
}
