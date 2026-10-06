package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Шаги «1С:УСК прислала …» о знаках после запятой у денежных полей прайса, программы и полиса. Программа и полис
 * строятся из присланных сценарием раньше, прайс — заново. Сырьё ложится так, как его сохранил бы приём из очереди;
 * ожидаемый исход разбора несёт само сообщение.
 */
public class OneCMoneyScaleSteps {

    private static final Map<String, BigDecimal> STEPS = Map.of(
            "на одну тысячную больше", OneCMoneyScaleMessages.ONE_THOUSANDTH,
            "на одну сотую больше", OneCMoneyLimitMessages.ONE_HUNDREDTH,
            "на одну сотую меньше", OneCMoneyLimitMessages.ONE_HUNDREDTH.negate());

    private final OneCInbox inbox;

    public OneCMoneyScaleSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала прайс услуги {string}, а в его строке поле {string} — число {string}")
    public void priceListSent(String service, String field, String number) {
        OneCMoneyScaleMessages.priceList(service, field, number).forEach(inbox::receive);
    }

    @Дано("1С:УСК прислала пакет {string} передачи прайса {string} по услуге {string} из одной строки, где поле {string} — число на одну тысячную больше {bigdecimal}")
    public void priceBatchSent(String messageId, String cid, String service, String field, BigDecimal value) {
        inbox.receive(OneCMoneyScaleMessages.priceBatch(UUID.fromString(messageId), UUID.fromString(cid), service, field,
                value.add(OneCMoneyScaleMessages.ONE_THOUSANDTH)));
    }

    @Дано("1С:УСК прислала ту же программу РВЛ под новым наименованием, а в ней ещё строка услуги {string} с процентом сооплаты с тремя знаками после запятой")
    public void programWithDroppedRowSent(String droppedRow) {
        inbox.receive(OneCMoneyScaleMessages.programWithDroppedRow(inbox.lastOf(OneCStream.PROGRAMS), droppedRow));
    }

    @Дано("1С:УСК прислала ту же программу без признака РВЛ, а в строке услуги процент сооплаты с тремя знаками после запятой")
    public void programWithoutRvlSent() {
        inbox.receive(OneCMoneyScaleMessages.programWithoutRvl(inbox.lastOf(OneCStream.PROGRAMS)));
    }

    @Дано("1С:УСК прислала тот же полис, а поле {string} — число {string}")
    public void policySent(String field, String number) {
        inbox.receive(OneCMoneyScaleMessages.policy(lastPolicy(), field, new BigDecimal(number)));
    }

    @Дано("1С:УСК прислала тот же полис, а поле {string} — число с двумя знаками после запятой")
    public void policyWithTwoDigitsSent(String field) {
        inbox.receive(OneCMoneyScaleMessages.policy(lastPolicy(), field, OneCMoneyScaleMessages.twoDigits(field)));
    }

    @Дано("1С:УСК прислала тот же полис под новым номером, а поле {string} — число с тремя знаками после запятой")
    public void policyWithThreeDigitsSent(String field) {
        inbox.receive(OneCMoneyScaleMessages.renamedPolicy(
                lastPolicy(), field, OneCMoneyScaleMessages.threeDigits(field)));
    }

    @Дано("1С:УСК прислала тот же полис под новым номером, а поле {string} — число {string}, чем {string}")
    public void policyPastValueSent(String field, String step, String number) {
        BigDecimal value = new BigDecimal(number).add(Optional.ofNullable(STEPS.get(step))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает слова: " + step)));
        inbox.receive(OneCMoneyScaleMessages.renamedPolicy(lastPolicy(), field, value));
    }

    private OneCMessage lastPolicy() {
        return inbox.lastOf(OneCStream.POLICIES);
    }
}
