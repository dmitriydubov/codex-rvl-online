package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.Map;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.JsonNode;

/**
 * Шаги о журнале Дата-референса при отказе разбора сообщения 1С:УСК.
 *
 * <p>Окончательный отказ виден записью уровня ERROR, в которой есть {@code message-id}, поток, номер неудачной
 * попытки, тип ошибки и SQLSTATE. Значений полей сообщения в журнале нет ни в тексте записи, ни в её контексте, ни
 * в тексте приложенного исключения: через этот путь идут полисы с персональными данными.
 */
public class OneCJournalSteps {

    /** Потоки так, как их называет 1С:УСК; неизвестное имя — ошибка сценария, а не другой поток. */
    private static final Map<String, OneCStream> STREAMS = Map.of(
            "контрагенты", OneCStream.CONTRAGENTS,
            "программы", OneCStream.PROGRAMS,
            "контактная информация", OneCStream.LPU_CONTACTS,
            "договоры страхования", OneCStream.INSURANCE_CONTRACTS,
            "полисы", OneCStream.POLICIES,
            "прейскуранты", OneCStream.PRICE_LISTS);

    private final OneCInbox inbox;
    private final ServiceJournal journal;

    public OneCJournalSteps(OneCInbox inbox, ServiceJournal journal) {
        this.inbox = inbox;
        this.journal = journal;
    }

    @Тогда("значение поля {string} последнего сообщения есть в его сырье, но не попало ни в причину отказа, ни в журнал Дата-референса")
    public void fieldValueKeptOnlyInRaw(String field) {
        JsonNode sent = OneCFailureMessages.nodeOf(inbox.lastMessage().data(), field);
        InboundRow last = inbox.last();
        JsonNode stored = OneCFailureMessages.storedNodeOf(last.payload(), field);

        assertThat(OneCFailureMessages.sameValue(sent, stored))
                .as("в теле последнего сообщения поле %s со значением как присланное", field).isTrue();
        for (String value : List.of(sent.asString(), stored.asString())) {
            assertThat(last.lastError()).as("причина отказа последнего сообщения").isNotNull().doesNotContain(value);
            assertThat(journal.mentioning(value)).as("записи журнала со значением поля %s", field).isEmpty();
        }
    }

    @Тогда("в журнале Дата-референса есть запись уровня ERROR об окончательном отказе последнего сообщения: поток {string}, попытка {int}, тип {string}, SQLSTATE {string}")
    public void finalRejectionLogged(String stream, int attempt, String type, String sqlState) {
        finalRejectionLogged(inbox.lastMessage().messageId().toString(), stream, attempt, type, sqlState);
    }

    @Тогда("в журнале Дата-референса есть запись уровня ERROR об окончательном отказе сообщения {string}: поток {string}, попытка {int}, тип {string}, SQLSTATE {string}")
    public void finalRejectionLogged(String messageId, String stream, int attempt, String type, String sqlState) {
        assertThat(STREAMS).as("потоки, которые знает шаг").containsKey(stream);
        String expected = "сообщение %s потока %s отклонено окончательно на попытке %d: %s, SQLSTATE %s".formatted(
                messageId, STREAMS.get(stream), attempt, type, sqlState);

        assertThat(journal.events())
                .as("запись ERROR об окончательном отказе: %s", expected)
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && expected.equals(event.getFormattedMessage())
                        && event.getThrowableProxy() == null);
    }
}
