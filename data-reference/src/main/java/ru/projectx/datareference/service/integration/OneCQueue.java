package ru.projectx.datareference.service.integration;

import java.util.function.Function;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Очередь брокера 1С:УСК: какому потоку она принадлежит и каким свойством задано её имя.
 * Здесь связь «очередь → поток» видна глазами — по строке на очередь.
 *
 * <p>Имени очереди здесь нет намеренно: его задаёт контур ({@link OneCQueueProperties}),
 * а связь с потоком — контракт, и он не меняется вместе с адресом.
 */
enum OneCQueue {

    POLICIES(OneCStream.POLICIES, OneCQueueProperties::policies),
    INSURANCE_CONTRACTS(OneCStream.INSURANCE_CONTRACTS, OneCQueueProperties::insuranceContracts),
    CONTRAGENTS(OneCStream.CONTRAGENTS, OneCQueueProperties::contragents),
    MKB10(OneCStream.MKB10, OneCQueueProperties::mkb10),
    LPU_CONTRACTS(OneCStream.LPU_CONTRACTS, OneCQueueProperties::lpuContracts),
    PROGRAMS(OneCStream.PROGRAMS, OneCQueueProperties::programs),
    APPEAL_TYPES(OneCStream.APPEAL_TYPES, OneCQueueProperties::appealTypes),
    LPU_CONTACTS(OneCStream.LPU_CONTACTS, OneCQueueProperties::lpuContacts),

    /** Пакеты строк прайса. Применяются не поодиночке, а в составе собранной передачи. */
    PRICE_LISTS(OneCStream.PRICE_LISTS, OneCQueueProperties::priceLists),

    /** Финальное сообщение передачи прайса — то, что несёт счётчики. Поток тот же. */
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

    /** Имя очереди на брокере контура. */
    String nameIn(OneCQueueProperties queues) {
        return nameProperty.apply(queues);
    }
}
