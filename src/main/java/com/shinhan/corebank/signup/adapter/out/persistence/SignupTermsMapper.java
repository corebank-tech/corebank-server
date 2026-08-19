package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

final class SignupTermsMapper {

    private SignupTermsMapper() {
    }

    static SignupTerm toDomain(
            SignupTermsJpaEntity entity
    ) {
        return new SignupTerm(
                entity.getTermsId().toString(),
                entity.getTermsCode(),
                entity.getVersion(),
                entity.getTitle(),
                entity.getContent(),
                entity.isRequired(),
                entity.isViewRequired()
        );
    }
}
