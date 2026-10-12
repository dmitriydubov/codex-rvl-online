package ru.projectx.contracts.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ContractNumberRepository {

    private static final String NEXT_NUMBER = """
            insert into contract_number_counters (year, last_number) values (:year, 1)
            on conflict (year) do update set last_number = contract_number_counters.last_number + 1
            returning last_number
            """;

    private final JdbcClient jdbc;

    public ContractNumberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int nextNumber(int year) {
        return jdbc.sql(NEXT_NUMBER).param("year", year).query(Integer.class).single();
    }
}
