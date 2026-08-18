package com.shinhan.corebank.product.adapter.out.terms;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.application.port.out.TermsDetail;
import com.shinhan.corebank.product.application.port.out.TermsSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MockTermsQueryPortTest extends IntegrationTestSupport {

    @Autowired
    MockTermsQueryPort mockTermsQueryPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("시드 데이터의 termsId로 조회하면 실제 terms 테이블 값을 반환한다")
    void findDetailById_returnsSeededTerms() {
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_DEPOSIT");

        Optional<TermsDetail> result = mockTermsQueryPort.findDetailById(termsId);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("예금거래 기본약관");
        assertThat(result.get().isRequired()).isTrue();
        assertThat(result.get().viewRequired()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 termsId면 빈 Optional을 반환한다")
    void findDetailById_notFound() {
        assertThat(mockTermsQueryPort.findDetailById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("findByIds는 요청한 termsId들의 요약 정보를 반환한다")
    void findByIds_returnsSummaries() {
        Long termsId = jdbcTemplate.queryForObject(
                "SELECT terms_id FROM terms WHERE terms_code = ?", Long.class, "TERMS_SAVINGS");

        List<TermsSummary> result = mockTermsQueryPort.findByIds(List.of(termsId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).termsId()).isEqualTo(termsId);
        assertThat(result.get(0).title()).isEqualTo("적립식예금 약관");
    }
}
