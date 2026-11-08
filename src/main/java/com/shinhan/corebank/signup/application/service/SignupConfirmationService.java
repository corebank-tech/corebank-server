package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.signup.application.port.in.GetSignupConfirmationUseCase;
import com.shinhan.corebank.signup.application.port.in.SignupConfirmationResult;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import com.shinhan.corebank.signup.domain.model.SignupConfirmation;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

// tempSignupToken으로 회원가입 확인정보를 조회하고 화면 계약에 맞게 마스킹한다.
@Service
public class SignupConfirmationService implements GetSignupConfirmationUseCase {

    private static final DateTimeFormatter BIRTH_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TempSignupTokenPort tempTokenPort;
    private final ExistingBankCustomerProfilePort profilePort;

    public SignupConfirmationService(TempSignupTokenPort tempTokenPort, ExistingBankCustomerProfilePort profilePort) {
        this.tempTokenPort = tempTokenPort;
        this.profilePort = profilePort;
    }

    @Override
    public SignupConfirmationResult getConfirmation(String tempSignupToken) {
        TempSignupTokenPayload payload = tempTokenPort
                .find(tempSignupToken)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT));
        ExistingBankCustomerProfile profile = profilePort
                .findByCustomerId(payload.existingBankCustomerId())
                .orElseThrow(() -> new IllegalStateException("Mock 은행 고객을 찾을 수 없습니다."));

        SignupConfirmation confirmation = new SignupConfirmation(
                MaskingUtil.maskName(profile.userName()),
                payload.userId(),
                profile.birthDate().format(BIRTH_FORMAT),
                MaskingUtil.maskPhoneNumber(payload.phoneNumber()),
                MaskingUtil.maskEmail(payload.email()));
        return SignupConfirmationResult.from(confirmation);
    }
}
