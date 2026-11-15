package com.shinhan.corebank.fds.adapter.out.persistence;

import com.shinhan.corebank.fds.application.port.out.BehaviorFlowMappingPort;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** refId와 flowId 양쪽 유니크 제약으로 한 이체와 한 흐름을 한 번만 연결한다. */
@Repository
@RequiredArgsConstructor
public class JdbcBehaviorFlowMappingAdapter implements BehaviorFlowMappingPort {

    private final JdbcTemplate jdbc;

    @Override
    public void insertOrKeepExisting(long refId, String flowId, long customerId, java.time.LocalDateTime createdAt) {
        jdbc.update(
                "INSERT INTO fds_transfer_behavior_flow(ref_id, flow_id, customer_id, created_at) "
                        + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE ref_id = ref_id",
                refId,
                flowId,
                customerId,
                Timestamp.valueOf(createdAt));
    }

    @Override
    public Mapping findByRefId(long refId) {
        return findByRefId(refId, false);
    }

    @Override
    public Mapping findByRefIdForUpdate(long refId) {
        return findByRefId(refId, true);
    }

    private Mapping findByRefId(long refId, boolean forUpdate) {
        return jdbc
                .query(
                        "SELECT ref_id, flow_id, customer_id FROM fds_transfer_behavior_flow WHERE ref_id = ?"
                                + (forUpdate ? " FOR UPDATE" : ""),
                        (rs, row) -> new Mapping(rs.getLong(1), rs.getString(2), rs.getLong(3)),
                        refId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Mapping findByFlowId(String flowId) {
        return jdbc
                .query(
                        "SELECT ref_id, flow_id, customer_id FROM fds_transfer_behavior_flow WHERE flow_id = ?",
                        (rs, row) -> new Mapping(rs.getLong(1), rs.getString(2), rs.getLong(3)),
                        flowId)
                .stream()
                .findFirst()
                .orElse(null);
    }
}
