package com.shinhan.infoapi.adapter.out.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface JdbcConnectionFactory {

    Connection open() throws SQLException;
}
