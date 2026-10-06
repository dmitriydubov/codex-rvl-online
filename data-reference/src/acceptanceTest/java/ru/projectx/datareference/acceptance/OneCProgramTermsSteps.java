package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Шаги «1С:УСК прислала ту же программу …» для обязательных условий строки услуги программы РВЛ. Сообщение строится
 * из программы, присланной сценарием раньше, и ложится в сырьё так, как его сохранил бы приём из очереди; ожидаемый
 * исход разбора несёт само сообщение.
 */
public class OneCProgramTermsSteps {

    private final OneCInbox inbox;

    public OneCProgramTermsSteps(OneCInbox inbox) {
        this.inbox = inbox;
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

    private OneCMessage lastProgram() {
        return inbox.lastOf(OneCStream.PROGRAMS);
    }
}
