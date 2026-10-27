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
               and tablename <> 'leader_lock'
            """;

    private final JdbcClient jdbc;
    private final List<StandQueue> queues;
    private final StatusQueue statusQueue;
    private final ReferenceStub reference;

    public StandCleanupHook(JdbcClient jdbc, List<StandQueue> queues, StatusQueue statusQueue, ReferenceStub reference) {
        this.jdbc = jdbc;
        this.queues = List.copyOf(queues);
        this.statusQueue = statusQueue;
        this.reference = reference;
    }

    @Before(order = 10)
    public void emptyStand() {
        statusQueue.declare();
        queues.forEach(StandQueue::purge);
        reference.reset();
        List<String> tables = jdbc.sql(DOMAIN_TABLES).query(String.class).list();
        jdbc.sql("truncate table " + String.join(", ", tables) + " cascade").update();
    }

    @Дано("нумерация договоров КМ начата заново")
    public void numberingRestarted() {
        jdbc.sql("delete from contract_number_counters").update();
    }
}
