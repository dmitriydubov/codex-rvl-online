package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Когда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.simple.JdbcClient;

public class InboxManualReturnSteps {

    private static final Path OPERATIONS = Path.of("docs", "operations.md");
    private static final String SECTION = "### Возврат отклонённого входящего сообщения в разбор";
    private static final String SQL_START = "```sql\n";
    private static final String SQL_END = "```";
    private static final String MESSAGE_ID = "<message-id>";

    private final InboxJournal journal;
    private final JdbcClient jdbc;

    public InboxManualReturnSteps(InboxJournal journal, JdbcClient jdbc) {
        this.journal = journal;
        this.jdbc = jdbc;
    }

    @Когда("дежурный возвращает в разбор сообщение {sender} {string} по процедуре ручного возврата")
    public void returnedByProcedure(MessageSender sender, String messageId) throws IOException {
        InboxJournal.Entry entry = journal.find(messageId)
                .orElseThrow(() -> new AssertionError("в журнале входящих нет записи о сообщении " + messageId));
        assertThat(sender.sends(entry.eventType())).as("сообщение %s прислал отправитель «%s»", messageId,
                sender.alias()).isTrue();
        jdbc.sql(procedure().replace(MESSAGE_ID, messageId)).update();
    }

    private static String procedure() throws IOException {
        String operations = Files.readString(OPERATIONS, StandardCharsets.UTF_8);
        int section = operations.indexOf(SECTION);
        assertThat(section).as("раздел «%s» в %s", SECTION, OPERATIONS).isNotNegative();
        int start = operations.indexOf(SQL_START, section) + SQL_START.length();
        return operations.substring(start, operations.indexOf(SQL_END, start)).strip();
    }
}
