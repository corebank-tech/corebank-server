package com.shinhan.corebank.fds.adapter.out.persistence;

import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
/** 행동 흐름과 이벤트를 순번 유니크 제약으로 저장한다. */
public class JdbcBehaviorFlowAdapter implements BehaviorFlowPort {

    private final JdbcTemplate jdbc;

    @Override
    public Flow find(String flowId) {
        return jdbc
                .query(
                        "SELECT flow_id, customer_id, first_received_at, finalized_at, confirm_active_seconds "
                                + "FROM fds_behavior_flow WHERE flow_id = ?",
                        (rs, row) -> new Flow(
                                rs.getString(1),
                                rs.getLong(2),
                                rs.getTimestamp(3).toLocalDateTime(),
                                rs.getTimestamp(4) == null
                                        ? null
                                        : rs.getTimestamp(4).toLocalDateTime(),
                                rs.getObject(5, Integer.class)),
                        flowId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void create(String flowId, long customerId, LocalDateTime receivedAt) {
        jdbc.update(
                "INSERT INTO fds_behavior_flow(flow_id, customer_id, first_received_at, last_received_at) "
                        + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE flow_id = flow_id",
                flowId,
                customerId,
                Timestamp.valueOf(receivedAt),
                Timestamp.valueOf(receivedAt));
    }

    @Override
    public void lock(String flowId) {
        jdbc.queryForList("SELECT flow_id FROM fds_behavior_flow WHERE flow_id = ? FOR UPDATE", String.class, flowId);
    }

    @Override
    public void append(String flowId, Event event, LocalDateTime receivedAt) {
        jdbc.update(
                "INSERT INTO fds_behavior_event(flow_id, seq_no, event_type, occurred_at, step_code, received_at) "
                        + "VALUES (?,?,?,?,?,?)",
                flowId,
                event.seqNo(),
                event.eventType(),
                Timestamp.valueOf(event.occurredAt()),
                event.step(),
                Timestamp.valueOf(receivedAt));
    }

    @Override
    public void finalizeFlow(String flowId, Integer confirmActiveSeconds, LocalDateTime receivedAt) {
        jdbc.update(
                "UPDATE fds_behavior_flow SET finalized_at = ?, confirm_active_seconds = ?, last_received_at = ? WHERE flow_id = ?",
                Timestamp.valueOf(receivedAt),
                confirmActiveSeconds,
                Timestamp.valueOf(receivedAt),
                flowId);
    }

    @Override
    public List<Event> events(String flowId) {
        return jdbc.query(
                "SELECT seq_no, event_type, occurred_at, step_code FROM fds_behavior_event WHERE flow_id = ? ORDER BY seq_no",
                (rs, row) -> new Event(
                        rs.getInt(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime(), rs.getString(4)),
                flowId);
    }
}
