package com.pelypets.todolist.server.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** PostgreSQL's JDBC driver maps {@code timestamptz} to {@link OffsetDateTime}, not to {@link Instant}. */
final class Timestamps {

    private Timestamps() {}

    static OffsetDateTime of(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant read(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }
}
