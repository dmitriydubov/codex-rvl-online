package ru.projectx.datareference.acceptance;

import io.cucumber.java.ru.Дано;
import java.util.List;
import java.util.Map;

public class OneCServiceSteps {

    private final OneCInbox inbox;

    public OneCServiceSteps(OneCInbox inbox) {
        this.inbox = inbox;
    }

    @Дано("1С:УСК прислала услугу ЛПУ:")
    public void serviceSent(Map<String, String> service) {
        inbox.receive(OneCServiceMessages.service(service));
    }

    @Дано("1С:УСК прислала прайс услуги {string}:")
    public void priceListSent(String serviceExternalId, List<Map<String, String>> rows) {
        for (OneCMessage message : OneCServiceMessages.priceList(serviceExternalId, rows)) {
            inbox.receive(message);
        }
    }
}
