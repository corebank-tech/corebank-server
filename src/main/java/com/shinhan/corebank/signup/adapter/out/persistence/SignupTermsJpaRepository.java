package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.signup.domain.model.TermsType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// 회원가입 유형의 최신 약관 행을 조회한다.
public interface SignupTermsJpaRepository
        extends JpaRepository<SignupTermsJpaEntity, Long> {

    @Query("""
            select term
            from SignupTermsJpaEntity term
            where term.termsType = :termsType
              and not exists (
                  select newer.termsId
                  from SignupTermsJpaEntity newer
                  where newer.termsType = term.termsType
                    and newer.termsCode = term.termsCode
                    and (
                        newer.createdAt > term.createdAt
                        or (
                            newer.createdAt = term.createdAt
                            and newer.termsId > term.termsId
                        )
                    )
              )
            order by term.termsId asc
            """)
    List<SignupTermsJpaEntity> findLatestByTermsType(
            TermsType termsType
    );

    Optional<SignupTermsJpaEntity> findByTermsIdAndVersion(
            Long termsId,
            String version
    );
}
