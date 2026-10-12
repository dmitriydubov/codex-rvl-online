package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;

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
