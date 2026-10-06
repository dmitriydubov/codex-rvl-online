package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;

/**
 * Шаги «1С:УСК прислала …» о составе ИНН и КПП контрагента. Сообщения ложатся в сырьё так, как их сохранил бы приём из
 * очереди, а разбирает их штатный проход планировщика (шаги {@link OneCSteps}); контрагент — слово из
 * {@link OneCFailureSteps#sanatorium(String)}.
 */
public class OneCRequisiteSteps {

    private final OneCInbox inbox;

    public OneCRequisiteSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала {контрагент} с полем \"ИНН\", в середине которого вместо одной цифры — {}")
    public void spoiledInnSent(Boolean sanatorium, String intruder) {
        inbox.receive(OneCRequisiteMessages.contragentWithSpoiledInn(sanatorium, intruder));
    }

    @Дано("1С:УСК прислала {контрагент} с КПП {string}")
    public void kppSent(Boolean sanatorium, String kind) {
        inbox.receive(OneCRequisiteMessages.contragentWithKpp(sanatorium, kind));
    }
}
