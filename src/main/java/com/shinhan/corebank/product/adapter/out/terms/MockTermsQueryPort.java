package com.shinhan.corebank.product.adapter.out.terms;

import com.shinhan.corebank.product.application.port.out.TermsDetail;
import com.shinhan.corebank.product.application.port.out.TermsQueryPort;
import com.shinhan.corebank.product.application.port.out.TermsSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// P6 terms 도메인 실제 구현 전까지 쓰는 임시 어댑터.
// terms 테이블에 대응하는 Entity/Repository가 프로젝트 어디에도 없어 JdbcTemplate으로 직접 읽는다.
// P6 구현체가 나오면 이 클래스를 실제 어댑터로 교체한다(포트/시그니처는 그대로).
// prod는 아직 실 트래픽 없는 초기 세팅 단계라 임시 허용 — P6 구현체 반영 시 반드시 제거할 것.
@Component
@Profile({"local", "test", "scratch", "prod"})
public class MockTermsQueryPort implements TermsQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public MockTermsQueryPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TermsSummary> findByIds(List<Long> termsIds) {
        if (termsIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", termsIds.stream().map(id -> "?").toList());
        return jdbcTemplate.query(
                "SELECT terms_id, title, version, is_required, view_required FROM terms WHERE terms_id IN (" + placeholders + ")",
                (rs, rowNum) -> new TermsSummary(
                        rs.getLong("terms_id"),
                        rs.getString("title"),
                        rs.getString("version"),
                        rs.getBoolean("is_required"),
                        rs.getBoolean("view_required")),
                termsIds.toArray());
    }

    @Override
    public Optional<TermsDetail> findDetailById(Long termsId) {
        List<TermsDetail> rows = jdbcTemplate.query(
                "SELECT terms_id, title, version, is_required, view_required, content FROM terms WHERE terms_id = ?",
                (rs, rowNum) -> new TermsDetail(
                        rs.getLong("terms_id"),
                        rs.getString("title"),
                        rs.getString("version"),
                        rs.getBoolean("is_required"),
                        rs.getBoolean("view_required"),
                        rs.getString("content")),
                termsId);
        return rows.stream().findFirst();
    }
}
