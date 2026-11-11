package ru.projectx.contracts.acceptance;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;

public class MarketingBrokerSteps {

    private static final int BEFORE_QUEUES_RESTORED = 20_000;

    private final MarketingBrokerOutage outage;

    public MarketingBrokerSteps(MarketingBrokerOutage outage) {
        this.outage = outage;
    }

    @Дано("брокер маркетинга недоступен для Договоров")
    public void brokerUnavailable() {
        outage.start();
    }

    @Когда("брокер маркетинга снова доступен")
    public void brokerAvailableAgain() {
        outage.end();
    }

    @After(order = BEFORE_QUEUES_RESTORED)
    public void restoreAccess() {
        outage.end();
    }
}
