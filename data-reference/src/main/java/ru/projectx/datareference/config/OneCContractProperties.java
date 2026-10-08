package ru.projectx.datareference.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Значения обмена с 1С:УСК, которые задаются при подключении к контуру. У значений, не названных в документах обмена,
 * умолчаний нет: GUID наугад дал бы пустой справочник без единой ошибки. Пока значение не задано, маппер не применяет
 * зависящее от него правило и называет это в причине пропуска сообщения.
 */
@Validated
@ConfigurationProperties("projectx.onec.contract")
public record OneCContractProperties(
        // Без вида деятельности «РВЛ» санатории не принимаются вовсе: отличить наш от любого юрлица 1С:УСК нечем.
        // Хранится как текст: каноническую запись GUID проверяет маппер контрагентов на старте, потому что стандартное
        // чтение UUID принимает и неполные записи.
        @Nullable String rvlActivityId,
        // Признак «договор на РВЛ» в свойствах договора ЛПУ (лечебно-профилактического учреждения); в документе
        // на месте имени заглушка. Пока имя не задано, договоры отбираются только по санаторию, то есть шире нужного:
        // лучше видеть лишние договоры, чем молча не видеть ни одного.
        @Nullable String rvlContractProperty,
        // Значение productType договора ДМС (добровольного медицинского страхования), его фиксируют при подключении.
        // Пока не задано, договоры страхования не принимаются: отличить ДМС от еОСАГО нечем, а чужие договоры не нужны.
        @Nullable String dmsProductType,
        // Значение поля type, которым строка контактов отмечена как адрес; телефоны и почта потоку адресов не нужны.
        @DefaultValue("Адрес") String addressRecordType,
        // Виды адреса по убыванию предпочтения; если ни один не совпал, берётся первая строка по rowNumber. Подписи
        // видов задаёт справочник 1С, и список в настройке позволяет уточнить их без выпуска сервиса.
        @DefaultValue({"Фактический адрес контрагента",
                       "Адрес местонахождения (регистрации) контрагента юр.лица"})
        List<String> addressKindPriority) {

    public Optional<String> rvlActivity() {
        return filled(rvlActivityId);
    }

    public Optional<String> rvlContract() {
        return filled(rvlContractProperty);
    }

    public Optional<String> dmsProduct() {
        return filled(dmsProductType);
    }

    /** Пустая строка в переменной окружения значит то же, что отсутствие настройки. */
    private static Optional<String> filled(@Nullable String setting) {
        return Optional.ofNullable(setting).filter(Predicate.not(String::isBlank));
    }
}
