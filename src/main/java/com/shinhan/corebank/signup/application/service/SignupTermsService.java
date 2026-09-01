package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementUseCase;
import com.shinhan.corebank.signup.application.port.in.GetSignupTermsUseCase;
import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.SignupTermsQueryPort;
import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.SignupTerm;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

// 회원가입 약관 조회·동의 검증·약관 인증 토큰 발급을 처리한다.
@Service
public class SignupTermsService implements GetSignupTermsUseCase, CheckTermsAgreementUseCase {

    private final SignupTermsQueryPort signupTermsQueryPort;
    private final TermsAuthTokenPort termsAuthTokenPort;
    private final AuthTokenGeneratorPort authTokenGeneratorPort;
    private final SignupTokenProperties tokenProperties;

    public SignupTermsService(
            SignupTermsQueryPort signupTermsQueryPort,
            TermsAuthTokenPort termsAuthTokenPort,
            AuthTokenGeneratorPort authTokenGeneratorPort,
            SignupTokenProperties tokenProperties) {
        this.signupTermsQueryPort = signupTermsQueryPort;
        this.termsAuthTokenPort = termsAuthTokenPort;
        this.authTokenGeneratorPort = authTokenGeneratorPort;
        this.tokenProperties = tokenProperties;
    }

    @Override
    public List<SignupTerm> getSignupTerms() {
        return signupTermsQueryPort.findLatestSignupTerms();
    }

    @Override
    public TermsAgreementResult checkTermsAgreement(CheckTermsAgreementCommand command) {
        List<SignupTerm> currentTerms = signupTermsQueryPort.findLatestSignupTerms();

        if (currentTerms.isEmpty()) {
            throw new IllegalStateException("회원가입 약관이 등록되어 있지 않습니다.");
        }

        validateDuplicateTerms(command);

        Map<String, SignupTerm> currentTermsById =
                currentTerms.stream().collect(Collectors.toMap(SignupTerm::termsId, Function.identity()));

        Map<String, CheckTermsAgreementCommand.Agreement> agreementsById = command.agreedTerms().stream()
                .collect(Collectors.toMap(CheckTermsAgreementCommand.Agreement::termsId, Function.identity()));

        validateRequestedTerms(command, currentTermsById);
        validateRequiredTerms(currentTerms, agreementsById);

        List<AgreedTerm> agreedTerms = command.agreedTerms().stream()
                .filter(CheckTermsAgreementCommand.Agreement::isAgreed)
                .map(agreement -> {
                    SignupTerm current = currentTermsById.get(agreement.termsId());

                    return new AgreedTerm(current.termsId(), current.version());
                })
                .sorted(Comparator.comparing(AgreedTerm::termsId))
                .toList();

        String token = authTokenGeneratorPort.generateTermsAuthToken();

        TermsAuthTokenPayload payload = new TermsAuthTokenPayload(agreedTerms, Instant.now());

        termsAuthTokenPort.save(token, payload, tokenProperties.termsAuthTtl());

        return new TermsAgreementResult(token, tokenProperties.termsAuthTtl().toSeconds());
    }

    private void validateDuplicateTerms(CheckTermsAgreementCommand command) {
        long distinctCount = command.agreedTerms().stream()
                .map(CheckTermsAgreementCommand.Agreement::termsId)
                .distinct()
                .count();

        if (distinctCount != command.agreedTerms().size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private void validateRequestedTerms(CheckTermsAgreementCommand command, Map<String, SignupTerm> currentTermsById) {
        for (CheckTermsAgreementCommand.Agreement agreement : command.agreedTerms()) {

            SignupTerm current = currentTermsById.get(agreement.termsId());

            if (current == null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }

            if (!current.version().equals(agreement.version())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "약관이 변경되었습니다. 다시 조회해 주세요.");
            }
        }
    }

    private void validateRequiredTerms(
            List<SignupTerm> currentTerms, Map<String, CheckTermsAgreementCommand.Agreement> agreementsById) {
        for (SignupTerm term : currentTerms) {
            if (!term.required()) {
                continue;
            }

            CheckTermsAgreementCommand.Agreement agreement = agreementsById.get(term.termsId());

            if (agreement == null || !agreement.isAgreed() || !agreement.isRead()) {
                throw new BusinessException(SignupErrorCode.REQUIRED_TERMS_NOT_AGREED);
            }
        }
    }
}
