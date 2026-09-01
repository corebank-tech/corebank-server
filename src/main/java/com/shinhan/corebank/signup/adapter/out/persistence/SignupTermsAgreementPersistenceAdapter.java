package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.application.port.out.SignupTermsAgreementPort;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

// 검증된 회원가입 약관 동의를 customer_terms_agreement에 저장한다.
@Component
public class SignupTermsAgreementPersistenceAdapter implements SignupTermsAgreementPort {

    private final SignupTermsJpaRepository termsRepository;
    private final CustomerTermsAgreementJpaRepository agreementRepository;

    public SignupTermsAgreementPersistenceAdapter(
            SignupTermsJpaRepository termsRepository, CustomerTermsAgreementJpaRepository agreementRepository) {
        this.termsRepository = termsRepository;
        this.agreementRepository = agreementRepository;
    }

    @Override
    public void saveAll(Long customerId, List<AgreedTerm> agreedTerms, LocalDateTime agreedAt) {
        List<CustomerTermsAgreementJpaEntity> entities = agreedTerms.stream()
                .map(term -> toEntity(customerId, term, agreedAt))
                .toList();
        agreementRepository.saveAll(entities);
    }

    private CustomerTermsAgreementJpaEntity toEntity(Long customerId, AgreedTerm agreedTerm, LocalDateTime agreedAt) {
        Long termsId = parseTermsId(agreedTerm.termsId());
        termsRepository
                .findByTermsIdAndVersion(termsId, agreedTerm.version())
                .orElseThrow(() -> new IllegalStateException("동의한 약관 식별자와 버전이 현재 약관 정보와 일치하지 않습니다."));
        return new CustomerTermsAgreementJpaEntity(customerId, termsId, agreedAt);
    }

    private Long parseTermsId(String termsId) {
        try {
            return Long.valueOf(termsId);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("임시 가입정보의 약관 식별자가 올바르지 않습니다.", exception);
        }
    }
}
