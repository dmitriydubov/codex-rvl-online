package ru.projectx.datareference.service.integration;

import java.util.function.Function;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.entity.OneCStream;

enum OneCQueue {

    POLICIES(OneCStream.POLICIES, OneCQueueProperties::policies),
    INSURANCE_CONTRACTS(OneCStream.INSURANCE_CONTRACTS, OneCQueueProperties::insuranceContracts),
    CONTRAGENTS(OneCStream.CONTRAGENTS, OneCQueueProperties::contragents),
    MKB10(OneCStream.MKB10, OneCQueueProperties::mkb10),
    LPU_CONTRACTS(OneCStream.LPU_CONTRACTS, OneCQueueProperties::lpuContracts),
    PROGRAMS(OneCStream.PROGRAMS, OneCQueueProperties::programs),
    APPEAL_TYPES(OneCStream.APPEAL_TYPES, OneCQueueProperties::appealTypes),
    LPU_CONTACTS(OneCStream.LPU_CONTACTS, OneCQueueProperties::lpuContacts),

    PRICE_LISTS(OneCStream.PRICE_LISTS, OneCQueueProperties::priceLists),

    PRICE_LISTS_FINAL(OneCStream.PRICE_LISTS, OneCQueueProperties::priceListsFinal),

    CONTRAGENT_SERVICES(OneCStream.CONTRAGENT_SERVICES, OneCQueueProperties::contragentServices);

    private final OneCStream stream;
    private final Function<OneCQueueProperties, String> nameProperty;

    OneCQueue(OneCStream stream, Function<OneCQueueProperties, String> nameProperty) {
        this.stream = stream;
        this.nameProperty = nameProperty;
    }

    OneCStream stream() {
        return stream;
    }

    String nameIn(OneCQueueProperties queues) {
        return nameProperty.apply(queues);
    }
}
