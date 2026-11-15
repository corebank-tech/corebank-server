package com.shinhan.corebank.fds.adapter.out.persistence;

import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort.Score;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 보류 건별 점수 상태를 조건부 SQL 전이로 저장한다. */
@Repository
@RequiredArgsConstructor
public class JdbcBehaviorScoreAdapter implements BehaviorScorePort {
    private final JdbcTemplate jdbc;

    @Override
    public void createIfAbsent(long holdId, String flowId, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO fds_behavior_score "
                        + "(transfer_hold_id, flow_id, score_status, created_at, updated_at) VALUES (?,?, 'PENDING', ?, ?) "
                        + "ON DUPLICATE KEY UPDATE transfer_hold_id = transfer_hold_id",
                holdId,
                flowId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now));
    }

    @Override
    public boolean markStarted(long holdId, LocalDateTime now) {
        return jdbc.update(
                        "UPDATE fds_behavior_score SET score_attempt_started_at = ?, updated_at = ? "
                                + "WHERE transfer_hold_id = ? AND score_status = 'PENDING' AND score_attempt_started_at IS NULL",
                        Timestamp.valueOf(now),
                        Timestamp.valueOf(now),
                        holdId)
                == 1;
    }

    @Override
    public boolean available(long holdId, Score score, LocalDateTime now) {
        return jdbc.update(
                        "UPDATE fds_behavior_score SET score_status = 'AVAILABLE', behavior_risk_score = ?, "
                                + "model_version = ?, feature_version = ?, updated_at = ? "
                                + "WHERE transfer_hold_id = ? AND score_status = 'PENDING'",
                        score.value(),
                        score.modelVersion(),
                        score.featureVersion(),
                        Timestamp.valueOf(now),
                        holdId)
                == 1;
    }

    @Override
    public boolean unavailable(long holdId, String reason, LocalDateTime now) {
        return jdbc.update(
                        "UPDATE fds_behavior_score SET score_status = 'UNAVAILABLE', unavailable_reason = ?, updated_at = ? "
                                + "WHERE transfer_hold_id = ? AND score_status = 'PENDING'",
                        reason,
                        Timestamp.valueOf(now),
                        holdId)
                == 1;
    }

    @Override
    public BehaviorScoreView find(long holdId) {
        return jdbc
                .query(
                        "SELECT transfer_hold_id, score_status, behavior_risk_score, model_version, "
                                + "feature_version, unavailable_reason FROM fds_behavior_score WHERE transfer_hold_id = ?",
                        (rs, row) -> new BehaviorScoreView(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getObject(3, Double.class),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getString(6)),
                        holdId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Long> startedBefore(LocalDateTime cutoff, int limit) {
        return jdbc.queryForList(
                "SELECT transfer_hold_id FROM fds_behavior_score "
                        + "WHERE score_status = 'PENDING' AND score_attempt_started_at < ? "
                        + "ORDER BY score_attempt_started_at LIMIT ?",
                Long.class,
                Timestamp.valueOf(cutoff),
                limit);
    }
}
