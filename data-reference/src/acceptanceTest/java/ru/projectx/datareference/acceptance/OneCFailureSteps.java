package ru.projectx.datareference.acceptance;

import io.cucumber.java.ParameterType;
import io.cucumber.java.ru.Дано;
import ru.projectx.datareference.entity.InboundStatus;

/**
 * Шаги «1С:УСК прислала …» для сценариев отказа разбора. Реквизит ровно в предел приходит в пробелах по краям:
 * пробелы в длину не входят, и разбор его принимает. Реквизит на цифру длиннее приходит без пробелов и отклоняется.
 */
public class OneCFailureSteps {

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
}
