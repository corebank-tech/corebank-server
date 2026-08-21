package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import java.time.LocalDateTime;
import java.util.List;

// 회원가입 완료 고객의 약관 동의 이력을 저장한다.
public interface SignupTermsAgreementPort {

    void saveAll(
            Long customerId,
            List<AgreedTerm> agreedTerms,
            LocalDateTime agreedAt
    );
}
