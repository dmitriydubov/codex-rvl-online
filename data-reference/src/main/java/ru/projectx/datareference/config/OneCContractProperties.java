package ru.projectx.datareference.config;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Значения обмена с 1С:УСК, которые зависят от конкретного контура и задаются при подключении к нему.
 *
 * <p>Они лежат в настройках по той же причине, что и имена очередей: это данные контура, а не свойство
 * протокола обмена. Значения, которые в документах обмена не названы, умолчаний не имеют намеренно.
 * Подставленный наугад GUID дал бы пустой справочник санаториев без единой ошибки в журнале: все
 * сообщения уходили бы в пропуск как чужие. Пока значение не задано, маппер не применяет зависящее от
 * него правило и пишет об этом в причине пропуска. Там, где документ называет значение прямо, умолчание
 * есть.
 *
 * @param rvlActivityId GUID вида деятельности «РВЛ» из списка видов деятельности контрагента. Без него
 *                санатории из потока контрагентов не принимаются вовсе: отличить наш санаторий от любого
 *                другого юрлица 1С:УСК нечем. Здесь хранится текст настройки как есть. Каноническую запись
 *                8-4-4-4-12 проверяет маппер контрагентов при старте сервиса по тому же правилу, что и GUID
 *                в сообщениях 1С:УСК, потому что стандартное чтение UUID принимает и неполные записи
 * @param rvlContractProperty имя признака «договор на РВЛ» в дополнительных свойствах договора ЛПУ. В
 *                документе на его месте заглушка, а в примере сообщения такого поля нет. Пока имя не
 *                задано, второй фильтр договоров ЛПУ не применяется, и договоры отбираются только по
 *                принадлежности нашему санаторию, то есть шире нужного. Это осознанный выбор: лучше
 *                видеть лишние договоры, чем молча не видеть ни одного
 * @param dmsProductType значение поля {@code productType}, по которому договор страхования признаётся
 *                договором ДМС. Документ называет «Личное страхование», но оговаривает, что значение
 *                фиксируется при подключении к контуру. Пока оно не задано, договоры страхования не
 *                принимаются вовсе: отличить ДМС от еОСАГО нечем, а сохранять всё подряд значит завести
 *                у себя чужие договоры
 * @param addressRecordType значение поля {@code type} строки контактов, которое означает адрес. Остальные
 *                строки, телефоны и почта, потоку адресов не нужны
 * @param addressKindPriority виды адреса в порядке предпочтения. Если подходящих строк несколько, берётся
 *                первая по этому списку, а если ни одна не совпала, то первая по {@code rowNumber}. Это
 *                список, а не две константы, потому что подписи видов задаёт справочник 1С, и уточнить их
 *                можно настройкой без выпуска новой версии сервиса
 */
@Validated
@ConfigurationProperties("projectx.onec.contract")
public record OneCContractProperties(
        @Nullable String rvlActivityId,
        @Nullable String rvlContractProperty,
        @Nullable String dmsProductType,
        @DefaultValue("Адрес") String addressRecordType,
        @DefaultValue({"Фактический адрес контрагента",
                       "Адрес местонахождения (регистрации) контрагента юр.лица"})
        List<String> addressKindPriority) {

    /** Текст настройки GUID вида деятельности «РВЛ»; пустая настройка означает, что значение не задано. */
    public Optional<String> rvlActivity() {
        return Optional.ofNullable(rvlActivityId).filter(id -> !id.isBlank());
    }

    public Optional<String> rvlContract() {
        return Optional.ofNullable(rvlContractProperty).filter(name -> !name.isBlank());
    }

    public Optional<String> dmsProduct() {
        return Optional.ofNullable(dmsProductType).filter(type -> !type.isBlank());
    }
}
