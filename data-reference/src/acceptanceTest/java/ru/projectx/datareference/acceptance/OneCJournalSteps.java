package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.projectx.datareference.entity.OneCStream;

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
            "договоры ЛПУ", OneCStream.LPU_CONTRACTS,
            "услуги контрагентов", OneCStream.CONTRAGENT_SERVICES,
            "программы", OneCStream.PROGRAMS,
            "контактная информация", OneCStream.LPU_CONTACTS,
            "договоры страхования", OneCStream.INSURANCE_CONTRACTS,
            "полисы", OneCStream.POLICIES,
            "прейскуранты", OneCStream.PRICE_LISTS);

    /** Сводная запись об истёкших ожиданиях потока: поток, число отклонённых и перечень GUID родителей. */
    private static final Pattern EXPIRED_WAITS = Pattern.compile(
            "сообщения потока (\\S+) не дождались родителя за срок ожидания \\S+: отклонено (\\d+), "
                    + "первые родители: \\[(.*)]");
    private static final Pattern GUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final OneCInbox inbox;
    private final ServiceJournal journal;

    public OneCJournalSteps(OneCInbox inbox, ServiceJournal journal) {
        this.inbox = inbox;
        this.journal = journal;
    }

    @Тогда("значение поля {string} последнего сообщения есть в его сырье, но не попало ни в причину отказа, ни в журнал Дата-референса")
    public void fieldValueKeptOnlyInRaw(String field) {
        String value = OneCFailureMessages.valueOf(inbox.lastMessage(), field);
        InboundRow last = inbox.last();

        assertThat(last.payload()).as("тело последнего сообщения").contains(value);
        assertThat(last.lastError()).as("причина отказа последнего сообщения").isNotNull().doesNotContain(value);
        assertThat(journal.mentioning(value)).as("записи журнала со значением поля %s", field).isEmpty();
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

    /**
     * Ровно одна запись ERROR за сценарий, и она сводная: называет поток, число отклонённых в проходе и ровно столько
     * GUID родителей, сколько сказано. Исключения к ней не приложено.
     */
    @Тогда("в журнале Дата-референса есть ровно одна запись уровня ERROR об истёкших ожиданиях потока {string}: истекло {int}, в перечне {int} GUID родителей")
    public void expiredWaitsSummaryLogged(String stream, int expired, int listed) {
        assertThat(STREAMS).as("потоки, которые знает шаг").containsKey(stream);
        ILoggingEvent summary = onlyError();
        Matcher matcher = EXPIRED_WAITS.matcher(summary.getFormattedMessage());

        assertThat(matcher.matches()).as("сводная запись об истёкших ожиданиях: %s", summary.getFormattedMessage())
                .isTrue();
        assertThat(matcher.group(1)).as("поток в сводной записи").isEqualTo(STREAMS.get(stream).name());
        assertThat(Integer.parseInt(matcher.group(2))).as("число истёкших в сводной записи").isEqualTo(expired);
        assertThat(GUID.matcher(matcher.group(3)).results().count()).as("GUID родителей в сводной записи")
                .isEqualTo(listed);
    }

    @Тогда("в журнале Дата-референса есть ровно одна запись уровня ERROR об истёкшем ожидании услуги передачи прайса {string}")
    public void expiredTransferLogged(String cid) {
        assertThat(onlyError().getFormattedMessage())
                .as("запись ERROR об истёкшем ожидании услуги передачей")
                .startsWith("передача прайса " + cid + " не дождалась услуги ");
    }

    private ILoggingEvent onlyError() {
        List<ILoggingEvent> errors = journal.events().stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();
        assertThat(errors).as("записи уровня ERROR в журнале").hasSize(1);
        assertThat(errors.getFirst().getThrowableProxy()).as("исключение, приложенное к записи ERROR").isNull();
        return errors.getFirst();
    }
}
