package ru.projectx.datareference.acceptance;

import io.cucumber.java.ParameterType;
import io.cucumber.java.ru.Дано;
import java.math.BigDecimal;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Шаги «1С:УСК прислала …» для сценариев отказа разбора. Сообщения ложатся в сырьё так, как их сохранил бы
 * приём из очереди, а разбирает их штатный проход планировщика (шаги {@link OneCSteps}).
 *
 * <p>Реквизит ровно в предел приходит в пробелах по краям: пробелы в длину не входят, и разбор принимает
 * значение. Реквизит на цифру длиннее — без пробелов, и разбор его отклоняет.
 */
public class OneCFailureSteps {

    private static final BigDecimal ONE_HUNDREDTH = new BigDecimal("0.01");

    private final OneCInbox inbox;

    public OneCFailureSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    /** Санаторий РВЛ или страхователь: другой контрагент шаг не знает. */
    @ParameterType(name = "контрагент", value = "санаторий РВЛ|страхователя")
    public Boolean sanatorium(String contragent) {
        return "санаторий РВЛ".equals(contragent);
    }

    @Дано("1С:УСК прислала {контрагент} с полем {string} из {int} цифр в пробелах по краям")
    public void requisiteAtLimitSent(Boolean sanatorium, String field, int length) {
        InboundStatus accepted = sanatorium ? InboundStatus.PROCESSED : InboundStatus.SKIPPED;
        inbox.receive(OneCFailureMessages.contragent(
                sanatorium, field, " " + OneCFailureMessages.digits(length) + " ", accepted));
    }

    @Дано("1С:УСК прислала {контрагент} с полем {string} из {int} цифр и ещё одной")
    public void requisiteOverLimitSent(Boolean sanatorium, String field, int length) {
        inbox.receive(OneCFailureMessages.contragent(
                sanatorium, field, OneCFailureMessages.digits(length + 1), InboundStatus.FAILED));
    }

    @Дано("1С:УСК прислала контактную информацию без GUID контрагента ни в фильтре, ни в строках")
    public void contactsWithoutContragentSent() {
        inbox.receive(OneCFailureMessages.contactsWithoutContragent());
    }

    /**
     * Процент с точкой и двумя знаками, как его пишет 1С:УСК. Встроенный тип {@code bigdecimal} читает число по
     * языку фичи, а в русском точка — разделитель тысяч, и 999.99 стало бы 99999.
     */
    @ParameterType(name = "процент", value = "\\d+\\.\\d{2}")
    public BigDecimal percent(String value) {
        return new BigDecimal(value);
    }

    @Дано("1С:УСК прислала программу РВЛ, у услуги которой процент сооплаты {процент}")
    public void programWithCopaymentSent(BigDecimal percent) {
        inbox.receive(OneCFailureMessages.program(lastSanatorium(), percent, InboundStatus.PROCESSED));
    }

    @Дано("1С:УСК прислала программу РВЛ, у услуги которой процент сооплаты на одну сотую больше {процент}")
    public void programWithCopaymentOverLimitSent(BigDecimal percent) {
        inbox.receive(OneCFailureMessages.program(lastSanatorium(), percent.add(ONE_HUNDREDTH), InboundStatus.FAILED));
    }

    /** Санаторий, присланный сценарием раньше: перечень учреждений программы ссылается на него. */
    private String lastSanatorium() {
        return inbox.lastOf(OneCStream.CONTRAGENTS).data().path("guid").asString();
    }
}
