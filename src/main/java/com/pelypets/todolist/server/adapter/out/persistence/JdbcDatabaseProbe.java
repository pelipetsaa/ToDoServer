package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.DatabaseProbe;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcDatabaseProbe implements DatabaseProbe {

    private final JdbcClient jdbc;

    JdbcDatabaseProbe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isReachable() {
        try {
            return jdbc.sql("SELECT 1").query(Integer.class).single() == 1;
        } catch (DataAccessException e) {
            return false;
        }
    }
}
