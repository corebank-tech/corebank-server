package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.domain.model.SignupTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SignupTermsPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    SignupTermsPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("SIGNUP 유형의 최신 약관을 조회한다")
    void findsLatestSignupTerms() {
        List<SignupTerm> result = adapter.findLatestSignupTerms();

        assertThat(result)
                .extracting(SignupTerm::termsCode)
                .contains(
                        "TERMS_SERVICE",
                        "TERMS_PRIVACY",
                        "TERMS_MARKETING"
                );
    }

    @Test
    @DisplayName("같은 termsCode에서는 생성시각이 가장 최신인 약관만 조회한다")
    void findsOnlyLatestVersionOfSameTermsCode() {
        insertTerms(
                "TERMS_LATEST_TEST",
                "v1.0",
                "SIGNUP",
                LocalDateTime.of(2026, 8, 18, 0, 0)
        );
        insertTerms(
                "TERMS_LATEST_TEST",
                "v2.0",
                "SIGNUP",
                LocalDateTime.of(2026, 8, 19, 0, 0)
        );

        List<SignupTerm> result = adapter.findLatestSignupTerms();

        assertThat(result)
                .filteredOn(term -> term.termsCode().equals("TERMS_LATEST_TEST"))
                .singleElement()
                .extracting(SignupTerm::version)
                .isEqualTo("v2.0");
    }

    @Test
    @DisplayName("PRODUCT 유형 약관은 회원가입 약관 조회에서 제외한다")
    void excludesProductTerms() {
        insertTerms(
                "TERMS_PRODUCT_EXCLUSION_TEST",
                "v1.0",
                "PRODUCT",
                LocalDateTime.of(2026, 8, 19, 0, 0)
        );

        List<SignupTerm> result = adapter.findLatestSignupTerms();

        assertThat(result)
                .extracting(SignupTerm::termsCode)
                .doesNotContain("TERMS_PRODUCT_EXCLUSION_TEST");
    }

    private void insertTerms(
            String termsCode,
            String version,
            String termsType,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO terms (
                            terms_code,
                            version,
                            terms_type,
                            title,
                            content,
                            is_required,
                            view_required,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                termsCode,
                version,
                termsType,
                termsCode + " 제목",
                termsCode + " 내용",
                true,
                false,
                createdAt,
                createdAt
        );
    }
}
