package ru.projectx.datareference.config;

import java.util.List;

/**
 * Имена кэшей справочных ручек и генератора ключей с днём контура. Поиск полисов и полис не кэшируются: в них
 * персональные данные, а изменение полиса должно быть видно сразу. Ответы, зависящие от сегодняшнего дня, начинают
 * ключ с этого дня ({@link #TODAY_KEY}), иначе вчерашний ответ пережил бы полночь.
 */
public final class ReferenceCaches {

    /** Каталог учреждений по договору страхования. Ключ — день и GUID договора. */
    public static final String LPU_CATALOG = "lpuCatalog";
    /** Карточка учреждения. Ключ — день и {@code id} учреждения. */
    public static final String LPU_CARD = "lpuCard";
    /** Услуги учреждения без цен. Ключ — день и {@code id} учреждения. */
    public static final String LPU_SERVICES = "lpuServices";
    /** Услуги учреждения с ценами. Ключ — день и {@code id} учреждения. */
    public static final String LPU_SERVICES_WITH_PRICES = "lpuServicesWithPrices";
    /** Стоимость услуги за период проживания. Ключ — день, {@code id} услуги, дата заезда и дата выезда. */
    public static final String SERVICE_PRICES = "servicePrices";
    /** Подсказка МКБ-10. Ключ — строка запроса и предел выдачи, как их прислал вызывающий. */
    public static final String MKB10_SEARCH = "mkb10Search";
    /** Раскрытие перечня кодов МКБ-10. Ключ — перечень кодов, как его прислал вызывающий. */
    public static final String MKB10_CODES = "mkb10Codes";
    /** Договор страхования по нашему {@code id}. */
    public static final String INSURANCE_CONTRACTS_BY_ID = "insuranceContractsById";
    /** Договор страхования по GUID 1С. */
    public static final String INSURANCE_CONTRACTS_BY_EXTERNAL_ID = "insuranceContractsByExternalId";
    /** Программа страхования по нашему {@code id}. */
    public static final String PROGRAMS = "programs";

    /** Все кэши: менеджер кэшей создаёт их при старте, и метрики видят каждый с первой минуты. */
    public static final List<String> ALL = List.of(
            LPU_CATALOG, LPU_CARD, LPU_SERVICES, LPU_SERVICES_WITH_PRICES, SERVICE_PRICES,
            MKB10_SEARCH, MKB10_CODES, INSURANCE_CONTRACTS_BY_ID, INSURANCE_CONTRACTS_BY_EXTERNAL_ID, PROGRAMS);

    /** Имя бина генератора ключей, который ставит в начало ключа сегодняшний день контура. */
    public static final String TODAY_KEY = "todayKeyGenerator";

    private ReferenceCaches() {
    }
}
