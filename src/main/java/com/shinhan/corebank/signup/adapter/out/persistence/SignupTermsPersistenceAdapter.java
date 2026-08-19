package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.application.port.out.SignupTermsQueryPort;
import com.shinhan.corebank.signup.domain.model.SignupTerm;
import com.shinhan.corebank.signup.domain.model.TermsType;
import org.springframework.stereotype.Component;

import java.util.List;

// 회원가입용 최신 약관을 JPA 저장소에서 조회한다.
@Component
public class SignupTermsPersistenceAdapter
        implements SignupTermsQueryPort {

    private final SignupTermsJpaRepository repository;

    public SignupTermsPersistenceAdapter(
            SignupTermsJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public List<SignupTerm> findLatestSignupTerms() {
        return repository.findLatestByTermsType(TermsType.SIGNUP)
                .stream()
                .map(SignupTermsMapper::toDomain)
                .toList();
    }
}
