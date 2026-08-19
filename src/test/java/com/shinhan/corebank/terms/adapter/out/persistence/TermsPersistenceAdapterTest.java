package com.shinhan.corebank.terms.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.terms.api.TermsDetail;
import com.shinhan.corebank.terms.api.TermsSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TermsPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    TermsPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("약관 ID로 조회하면 공용 상세 약관 계약으로 반환한다")
    void findDetailByIdReturnsTermsDetail() {
        Long termsId = findTermsId("TERMS_DEPOSIT");

        Optional<TermsDetail> result = adapter.findDetailById(termsId);

        assertThat(result).isPresent();
        assertThat(result.get().termsId()).isEqualTo(termsId);
        assertThat(result.get().title()).isNotBlank();
        assertThat(result.get().content()).isNotNull();
        assertThat(result.get().isRequired()).isTrue();
        assertThat(result.get().viewRequired()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 약관 ID는 빈 Optional을 반환한다")
    void findDetailByIdReturnsEmptyWhenNotFound() {
        assertThat(adapter.findDetailById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("여러 약관 ID의 요약 정보를 공용 계약으로 반환한다")
    void findByIdsReturnsTermsSummaries() {
        Long depositTermsId = findTermsId("TERMS_DEPOSIT");
        Long savingsTermsId = findTermsId("TERMS_SAVINGS");

        List<TermsSummary> result = adapter.findByIds(
                List.of(depositTermsId, savingsTermsId)
        );

        assertThat(result)
                .extracting(TermsSummary::termsId)
                .containsExactlyInAnyOrder(depositTermsId, savingsTermsId);
    }

    @Test
    @DisplayName("빈 약관 ID 목록은 데이터베이스를 조회하지 않고 빈 목록을 반환한다")
    void findByIdsReturnsEmptyListForEmptyInput() {
        assertThat(adapter.findByIds(List.of())).isEmpty();
    }

    private Long findTermsId(String termsCode) {
        return jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?",
                Long.class,
                termsCode
        );
    }
}
