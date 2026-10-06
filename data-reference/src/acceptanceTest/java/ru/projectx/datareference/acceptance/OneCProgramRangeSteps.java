package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Шаги «1С:УСК прислала ту же программу …» о пределах слоя и процента сооплаты строки услуги программы РВЛ. Сообщение
 * строится из программы, присланной сценарием раньше, и ложится в сырьё так, как его сохранил бы приём из очереди;
 * ожидаемый исход разбора несёт само сообщение.
 */
public class OneCProgramRangeSteps {

    private final OneCInbox inbox;

    public OneCProgramRangeSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала ту же программу РВЛ, а в строке услуги поле {string} — число {string}")
    public void programWithTermSent(String field, String number) {
        inbox.receive(OneCProgramRangeMessages.withTerm(lastProgram(), field, number));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в строке услуги поле {string} — число {string}, чем {string}")
    public void programWithTermPastLimitSent(String field, String stepPastLimit, String limit) {
        inbox.receive(OneCProgramRangeMessages.withTermPastLimit(lastProgram(), field, stepPastLimit, limit));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в ней ещё строка услуги {string} с полем {string} за пределом")
    public void programWithDroppedRowPastLimitSent(String droppedRow, String field) {
        inbox.receive(OneCProgramRangeMessages.withDroppedRowPastLimit(lastProgram(), droppedRow, field));
    }

    @Дано("1С:УСК прислала ту же программу без признака РВЛ, а в строке услуги поле {string} за пределом")
    public void programWithoutRvlPastLimitSent(String field) {
        inbox.receive(OneCProgramRangeMessages.withoutRvlPastLimit(lastProgram(), field));
    }

    private OneCMessage lastProgram() {
        return inbox.lastOf(OneCStream.PROGRAMS);
    }
}
