package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class OperationsProcedures {

    private static final Path OPERATIONS = Path.of("docs", "operations.md");
    private static final String SQL_START = "```sql\n";
    private static final String SQL_END = "```";
    private static final String CONTRACT_ID = "<contract-id>";

    private final JdbcClient jdbc;

    public OperationsProcedures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    int runForContract(String heading, UUID contractId) {
        String sql = firstSqlUnder(heading);
        assertThat(sql).as("запрос процедуры «%s»", heading).contains(CONTRACT_ID);
        return jdbc.sql(sql.replace(CONTRACT_ID, contractId.toString())).update();
    }

    private static String firstSqlUnder(String heading) {
        String operations = read();
        int section = operations.indexOf(heading + "\n");
        assertThat(section).as("раздел «%s» в %s", heading, OPERATIONS).isNotNegative();
        int start = operations.indexOf(SQL_START, section) + SQL_START.length();
        return operations.substring(start, operations.indexOf(SQL_END, start)).strip();
    }

    private static String read() {
        try {
            return Files.readString(OPERATIONS, StandardCharsets.UTF_8);
        }
        catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
