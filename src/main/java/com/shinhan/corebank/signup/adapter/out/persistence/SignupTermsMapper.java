package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

// 약관 JPA 엔티티를 회원가입 약관 도메인 모델로 변환한다.
final class SignupTermsMapper {

    private SignupTermsMapper() {}

    static SignupTerm toDomain(SignupTermsJpaEntity entity) {
        return new SignupTerm(
                entity.getTermsId().toString(),
                entity.getTermsCode(),
                entity.getVersion(),
                entity.getTitle(),
                entity.getContent(),
                entity.isRequired(),
                entity.isViewRequired());
    }
}
