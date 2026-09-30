package ru.projectx.datareference.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Значения контракта 1С, которые ТЗ оставило плейсхолдерами и которые фиксируются при
 * подключении к контуру.
 *
 * <p>Они в конфигурации по той же причине, по которой там же имена очередей: это данные
 * конкретного контура, а не свойство протокола.
 *
 * <p><b>Умолчаний у плейсхолдеров нет намеренно.</b> Подставить сюда произвольный GUID
 * значило бы получить пустой справочник санаториев без единой ошибки в логе — все
 * сообщения аккуратно уходили бы в пропуск как «не РВЛ». Пока значение не задано, маппер
 * честно отказывается применять и пишет это причиной пропуска. Там же, где ТЗ назвало
 * значение прямым текстом, умолчание есть.
 *
 * @param rvlActivityId GUID вида деятельности «РВЛ» из {@code typesActivities[]} (ТЗ-3,
 *                п. 3.2 — в документе плейсхолдер). Без него ветка санаториев потока
 *                ТЗ-3 не применяется вовсе: отличить наш санаторий от любого другого
 *                юрлица 1С:УСК нечем
 * @param rvlContractProperty имя признака «договор на РВЛ» в {@code extProperties}
 *                договора (ТЗ-5, фильтр 2 — в документе плейсхолдер, поля нет и в примере
 *                сообщения, вопрос {@code OQ-24}). Пока не задано, второй фильтр ТЗ-5
 *                не применяется: договоры отбираются только по принадлежности нашему
 *                санаторию, то есть <b>шире</b> предписанного. Это осознанный выбор
 *                в пользу «видно и избыточно» против «пусто и молча»
 * @param dmsProductType значение поля {@code productType}, по которому договор страхования
 *                признаётся договором ДМС (ТЗ-2). Документ называет «Личное страхование»,
 *                но оговаривает, что значение фиксируется при подключении к контуру,
 *                поэтому оно здесь, а не константой. Пока не задано, поток ТЗ-2
 *                не применяется вовсе: отличить ДМС от еОСАГО нечем, а сохранить всё
 *                подряд значит завести у себя чужие договоры
 * @param addressRecordType значение поля {@code type} строки контактов, означающее адрес
 *                (ТЗ-8). Остальные строки — телефоны и почта — этому потоку не нужны
 * @param addressKindPriority виды адреса в порядке предпочтения (ТЗ-8): при нескольких
 *                подходящих строках берётся первая по этому списку, а если ни одна
 *                не совпала — первая по {@code rowNumber}. Список, а не две константы:
 *                подписи видов задаёт справочник 1С, и уточнить их — вопрос конфигурации,
 *                а не выпуска новой версии сервиса
 */
@Validated
@ConfigurationProperties("projectx.onec.contract")
public record OneCContractProperties(
        @Nullable UUID rvlActivityId,
        @Nullable String rvlContractProperty,
        @Nullable String dmsProductType,
        @DefaultValue("Адрес") String addressRecordType,
        @DefaultValue({"Фактический адрес контрагента",
                       "Адрес местонахождения (регистрации) контрагента юр.лица"})
        List<String> addressKindPriority) {

    public Optional<UUID> rvlActivity() {
        return Optional.ofNullable(rvlActivityId);
    }

    public Optional<String> rvlContract() {
        return Optional.ofNullable(rvlContractProperty).filter(name -> !name.isBlank());
    }

    public Optional<String> dmsProduct() {
        return Optional.ofNullable(dmsProductType).filter(type -> !type.isBlank());
    }
}
