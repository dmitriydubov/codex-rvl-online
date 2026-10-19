package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import ru.projectx.datareference.entity.inbound.OneCStream;
import tools.jackson.databind.node.ObjectNode;

public class OneCProgramTermsSteps {

    private final OneCInbox inbox;
    private final ServiceJournal journal;

    public OneCProgramTermsSteps(OneCInbox inbox, ServiceJournal journal) {
        this.inbox = inbox;
        this.journal = journal;
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в строке услуги поле {string} — {string}")
    public void programWithTermSent(String field, String sentAs) {
        inbox.receive(OneCProgramTermsMessages.withTerm(lastProgram(), field, sentAs));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в ней ещё строка услуги {string} без поля {string}")
    public void programWithDroppedRowSent(String droppedRow, String field) {
        inbox.receive(OneCProgramTermsMessages.withDroppedRow(lastProgram(), droppedRow, field));
    }

    @Дано("1С:УСК прислала ту же программу без признака РВЛ, а в строке услуги поля {string} нет")
    public void programWithoutRvlSent(String field) {
        inbox.receive(OneCProgramTermsMessages.withoutRvl(lastProgram(), field));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в строке услуги рисковая часть {string} и депозитная часть {string}")
    public void programWithCoverageSent(String risk, String deposit) {
        inbox.receive(OneCProgramTermsMessages.withCoverage(lastProgram(), risk, deposit));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в ней ещё строка услуги {string}")
    public void programWithDroppedRowSent(String droppedRow) {
        inbox.receive(OneCProgramTermsMessages.withDroppedRow(lastProgram(), droppedRow));
    }

    @Тогда("в журнале Дата-референса есть запись уровня INFO об отброшенных строках перечня программы: с санаторием вне справочника {int}, без типа покрытия {int}")
    public void droppedRowsLogged(int unknownSanatorium, int withoutCoverage) {
        ObjectNode program = lastProgram().data();
        String expected = ("программа %s: %d из %d строк перечня отброшено — %d с санаторием вне справочника, "
                + "%d без типа покрытия").formatted(program.path("id").asString(), unknownSanatorium + withoutCoverage,
                        program.path("service").size(), unknownSanatorium, withoutCoverage);

        assertThat(journal.events())
                .as("запись INFO об отброшенных строках: %s", expected)
                .anyMatch(event -> event.getLevel() == Level.INFO && expected.equals(event.getFormattedMessage()));
    }

    private OneCMessage lastProgram() {
        return inbox.lastOf(OneCStream.PROGRAMS);
    }
}
