package ru.projectx.contracts.acceptance;

import io.cucumber.java.Before;
import io.cucumber.java.ru.Дано;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

public class StandCleanupHook {

    private static final String DOMAIN_TABLES = """
            select quote_ident(tablename)
              from pg_tables
             where schemaname = 'public'
               and tablename not like 'databasechangelog%'
            """;

    private final JdbcClient jdbc;
    private final TaskQueue queue;
    private final StatusQueue statusQueue;

    public StandCleanupHook(JdbcClient jdbc, TaskQueue queue, StatusQueue statusQueue) {
        this.jdbc = jdbc;
        this.queue = queue;
        this.statusQueue = statusQueue;
    }

    @Before(order = 10)
    public void emptyStand() {
        queue.purge();
        statusQueue.declare();
        statusQueue.purge();
        List<String> tables = jdbc.sql(DOMAIN_TABLES).query(String.class).list();
        jdbc.sql("truncate table " + String.join(", ", tables) + " cascade").update();
    }

    @Дано("нумерация договоров КМ начата заново")
    public void numberingRestarted() {
        jdbc.sql("delete from contract_number_counters").update();
    }
}
