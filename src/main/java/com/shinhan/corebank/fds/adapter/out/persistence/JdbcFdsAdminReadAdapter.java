package com.shinhan.corebank.fds.adapter.out.persistence;

import com.shinhan.corebank.fds.application.port.out.FdsAdminReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 관리자 화면은 외부 Tool을 재호출하지 않고 조사 당시 P6 스냅샷만 읽는다. */
@Repository
@RequiredArgsConstructor
public class JdbcFdsAdminReadAdapter implements FdsAdminReadPort {
    private final JdbcTemplate jdbc;

    @Override
    public List<Long> findHoldIds() {
        return jdbc.queryForList(
                "SELECT transfer_hold_id FROM fds_investigation_job ORDER BY created_at DESC, transfer_hold_id DESC",
                Long.class);
    }

    @Override
    public Report findReport(String investigationId) {
        return jdbc
                .query(
                        "SELECT schema_version,report_json,created_at FROM fds_investigation_report WHERE investigation_id=?",
                        (rs, row) -> new Report(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getTimestamp(3).toLocalDateTime()),
                        investigationId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public ToolResults findToolResults(String investigationId) {
        History history = jdbc
                .query(
                        "SELECT range_from,range_to,has_more,provided_at FROM fds_investigation_history_snapshot "
                                + "WHERE investigation_id=?",
                        (rs, row) -> new History(
                                rs.getTimestamp(1).toLocalDateTime(),
                                rs.getTimestamp(2).toLocalDateTime(),
                                rs.getBoolean(3),
                                rs.getTimestamp(4).toLocalDateTime(),
                                historyItems(investigationId)),
                        investigationId)
                .stream()
                .findFirst()
                .orElse(null);
        Recipient recipient = jdbc
                .query(
                        "SELECT first_succeeded_at,succeeded_before_window,other_withdrawal_account_count,provided_at "
                                + "FROM fds_investigation_recipient_snapshot WHERE investigation_id=?",
                        (rs, row) -> new Recipient(
                                nullableTime(rs, 1),
                                rs.getBoolean(2),
                                rs.getInt(3),
                                rs.getTimestamp(4).toLocalDateTime()),
                        investigationId)
                .stream()
                .findFirst()
                .orElse(null);
        return new ToolResults(history, recipient);
    }

    private List<HistoryItem> historyItems(String investigationId) {
        return jdbc.query(
                "SELECT ref_id,amount,transferred_at,recipient_account_masked FROM fds_investigation_history_item "
                        + "WHERE investigation_id=? ORDER BY transferred_at DESC,ref_id DESC",
                (rs, row) -> new HistoryItem(
                        rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toLocalDateTime(), rs.getString(4)),
                investigationId);
    }

    @Override
    public List<CallRecord> findCallRecords(long holdId) {
        return jdbc.query(
                "SELECT c.call_record_id,c.call_result,c.admin_id,c.investigation_reason,c.recorded_at,"
                        + "EXISTS(SELECT 1 FROM fds_admin_action a WHERE a.call_record_id=c.call_record_id) "
                        + "FROM fds_admin_call_record c WHERE c.transfer_hold_id=? "
                        + "ORDER BY c.recorded_at DESC,c.call_record_id DESC",
                (rs, row) -> new CallRecord(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime(),
                        rs.getBoolean(6)),
                holdId);
    }

    @Override
    public CallRecord findLatestCallRecord(long holdId) {
        return jdbc
                .query(
                        "SELECT c.call_record_id,c.call_result,c.admin_id,c.investigation_reason,c.recorded_at,"
                                + "EXISTS(SELECT 1 FROM fds_admin_action a WHERE a.call_record_id=c.call_record_id) "
                                + "FROM fds_admin_call_record c WHERE c.transfer_hold_id=? "
                                + "ORDER BY c.recorded_at DESC,c.call_record_id DESC LIMIT 1",
                        (rs, row) -> new CallRecord(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getLong(3),
                                rs.getString(4),
                                rs.getTimestamp(5).toLocalDateTime(),
                                rs.getBoolean(6)),
                        holdId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public AdminAction findLatestAction(long holdId) {
        return jdbc
                .query(
                        "SELECT action_id,action_type,action_status,actor_id,reason_code,call_record_id,risk_status,"
                                + "risk_account_id,dataset_version,p4_status,p4_actor_id,p4_reason_code,failure_reason,"
                                + "created_at,updated_at FROM fds_admin_action WHERE transfer_hold_id=? "
                                + "ORDER BY created_at DESC,action_id DESC LIMIT 1",
                        (rs, row) -> action(rs),
                        holdId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static AdminAction action(ResultSet rs) throws SQLException {
        return new AdminAction(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                (Long) rs.getObject(4),
                rs.getString(5),
                (Long) rs.getObject(6),
                rs.getString(7),
                (Long) rs.getObject(8),
                rs.getString(9),
                rs.getString(10),
                (Long) rs.getObject(11),
                rs.getString(12),
                rs.getString(13),
                rs.getTimestamp(14).toLocalDateTime(),
                rs.getTimestamp(15).toLocalDateTime());
    }

    private static java.time.LocalDateTime nullableTime(ResultSet rs, int column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }
}
