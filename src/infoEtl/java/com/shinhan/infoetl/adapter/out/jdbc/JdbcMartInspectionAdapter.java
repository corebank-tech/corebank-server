package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.MartInspectionPort;
import com.shinhan.infoetl.domain.MartColumn;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class JdbcMartInspectionAdapter implements MartInspectionPort {

    // 테이블·컬럼 이름은 information_schema에서 읽은 값이지만 SQL에 붙이므로 식별자 모양만 허용한다.
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_]+");

    private final JdbcConnectionFactory connections;

    public JdbcMartInspectionAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public Map<String, List<MartColumn>> readColumns() {
        Map<String, List<MartColumn>> tables = new LinkedHashMap<>();
        try (Connection mart = connections.open();
                PreparedStatement statement = mart.prepareStatement(
                        "SELECT table_name, column_name, data_type FROM information_schema.columns"
                                + " WHERE table_schema = DATABASE() AND table_name LIKE 'mart\\_%'"
                                + " ORDER BY table_name, ordinal_position");
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tables.computeIfAbsent(resultSet.getString(1).toLowerCase(), key -> new ArrayList<>())
                        .add(new MartColumn(resultSet.getString(2).toLowerCase(), resultSet.getString(3)));
            }
        } catch (SQLException e) {
            throw new EtlStorageException("마트 컬럼 조회에 실패했습니다: " + e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public long countMatching(String table, String column, String regex) {
        return count(table, column, regex, "");
    }

    @Override
    public long countNotMatching(String table, String column, String regex) {
        return count(table, column, regex, "NOT ");
    }

    private long count(String table, String column, String regex, String negation) {
        requireIdentifier(table);
        requireIdentifier(column);
        // 대소문자를 구분해 비교하려고 REGEXP_LIKE의 'c' 옵션을 쓴다(키는 소문자 16진수만 허용).
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NOT NULL AND " + negation
                + "REGEXP_LIKE(" + column + ", ?, 'c')";
        try (Connection mart = connections.open();
                PreparedStatement statement = mart.prepareStatement(sql)) {
            statement.setString(1, regex);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new EtlStorageException("마트 값 검사에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private static void requireIdentifier(String name) {
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("허용되지 않는 식별자입니다: " + name);
        }
    }
}
