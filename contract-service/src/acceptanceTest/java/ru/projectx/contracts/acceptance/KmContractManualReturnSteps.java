package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Когда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.simple.JdbcClient;

public class KmContractManualReturnSteps {

    private static final Path OPERATIONS = Path.of("docs", "operations.md");
    private static final String SECTION = "### Возврат договора КМ, не переданного в 1С:УСК, в оплаченные";
    private static final String SQL_START = "```sql\n";
    private static final String SQL_END = "```";
    private static final String CONTRACT_ID = "<contract-id>";

    private final IssuanceRecords records;
    private final JdbcClient jdbc;

    public KmContractManualReturnSteps(IssuanceRecords records, JdbcClient jdbc) {
        this.records = records;
        this.jdbc = jdbc;
    }

    @Когда("дежурный возвращает договор КМ участника {string} заявки {string} в оплаченные по процедуре ручного возврата")
    public void returnedByProcedure(String participantId, String requestId) throws IOException {
        String contractId = records.latestContractId(participantId, requestId).toString();
        jdbc.sql(procedure().replace(CONTRACT_ID, contractId)).update();
    }

    private static String procedure() throws IOException {
        String operations = Files.readString(OPERATIONS, StandardCharsets.UTF_8);
        int section = operations.indexOf(SECTION);
        assertThat(section).as("раздел «%s» в %s", SECTION, OPERATIONS).isNotNegative();
        int start = operations.indexOf(SQL_START, section) + SQL_START.length();
        return operations.substring(start, operations.indexOf(SQL_END, start)).strip();
    }
}
