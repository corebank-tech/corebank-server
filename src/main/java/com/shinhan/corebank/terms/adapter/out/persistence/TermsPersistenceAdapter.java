package com.shinhan.corebank.terms.adapter.out.persistence;

import com.shinhan.corebank.terms.api.TermsDetail;
import com.shinhan.corebank.terms.api.TermsQueryPort;
import com.shinhan.corebank.terms.api.TermsSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TermsPersistenceAdapter implements TermsQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public TermsPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TermsSummary> findByIds(List<Long> termsIds) {
        if (termsIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", termsIds.stream()
                .map(termsId -> "?")
                .toList());

        return jdbcTemplate.query(
                "SELECT terms_id, title, version, is_required, view_required "
                        + "FROM terms WHERE terms_id IN (" + placeholders + ")",
                (resultSet, rowNumber) -> new TermsSummary(
                        resultSet.getLong("terms_id"),
                        resultSet.getString("title"),
                        resultSet.getString("version"),
                        resultSet.getBoolean("is_required"),
                        resultSet.getBoolean("view_required")
                ),
                termsIds.toArray()
        );
    }

    @Override
    public Optional<TermsDetail> findDetailById(Long termsId) {
        List<TermsDetail> terms = jdbcTemplate.query(
                "SELECT terms_id, title, version, is_required, view_required, content "
                        + "FROM terms WHERE terms_id = ?",
                (resultSet, rowNumber) -> new TermsDetail(
                        resultSet.getLong("terms_id"),
                        resultSet.getString("title"),
                        resultSet.getString("version"),
                        resultSet.getBoolean("is_required"),
                        resultSet.getBoolean("view_required"),
                        resultSet.getString("content")
                ),
                termsId
        );

        return terms.stream().findFirst();
    }
}
