package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Разбор накопленного сырья: как часто заглядывать в таблицу и сколько сообщений
 * брать за раз.
 *
 * @param pollInterval период опроса. Читается ещё и плейсхолдером в {@code @Scheduled}
 *                самого шедулера: {@code fixedDelayString} значения записи не видит,
 *                поэтому умолчание продублировано там же строкой. Разойтись они могут
 *                только вместе с опечаткой в имени свойства
 * @param initialDelay сколько планировщики сырья ждут после старта до первого прохода —
 *                общая задержка для разбора, срока хранения и присмотра за передачами.
 *                Без неё первый тик случается прямо на старте контекста, раньше, чем сервис
 *                готов к работе. Планировщики читают её плейсхолдером
 *                {@link #INITIAL_DELAY_PLACEHOLDER}, который собран из того же умолчания,
 *                что и запись; запись нужна, чтобы значение проверялось на старте:
 *                отрицательное {@code @Scheduled} молча принял бы за нулевое
 * @param batchSize сколько сообщений разбирается за один проход. Ограничение есть,
 *                потому что первичная загрузка справочников — это десятки тысяч
 *                сообщений, и разбирать их одним проходом значит держать транзакцию
 *                и память под всю выгрузку сразу
 * @param stuckTransferThreshold сколько многочастная передача (ТЗ-9) может ждать своих
 *                частей, прежде чем это станет поводом для тревоги. Передача, часть
 *                которой потерялась у источника, не соберётся никогда: прайс по её услуге
 *                тихо останется вчерашним, и заметить это можно только по давности
 * @param payloadTtl сколько тело сообщения с персональными данными живёт в таблице сырья
 *                после приёма. Тридцать суток — заметно больше любого разумного срока
 *                разбора инцидента и заметно меньше срока, на котором таблица сырья
 *                превращается во вторую копию реестра застрахованных
 * @param retentionBatchSize сколько строк чистится за один проход: весь остаток одним
 *                {@code UPDATE} заблокировал бы таблицу, по которой в это же время идёт
 *                разбор
 */
@Validated
@ConfigurationProperties("projectx.import")
public record ImportProperties(
        @DefaultValue("10s") Duration pollInterval,
        @DefaultValue(INITIAL_DELAY_DEFAULT) @DurationMin(millis = 0) Duration initialDelay,
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("1h") Duration stuckTransferThreshold,
        @DefaultValue("30d") Duration payloadTtl,
        @DefaultValue("500") @Positive int retentionBatchSize) {

    /**
     * Полминуты: первый проход отодвинут от старта контекста, но сырьё, накопленное
     * за перезапуск, ждёт разбора не дольше трёх периодов опроса по умолчанию.
     */
    private static final String INITIAL_DELAY_DEFAULT = "30s";

    /**
     * Задержка первого тика для {@code @Scheduled(initialDelayString)}: аннотация принимает
     * только строку-константу, значения записи она не видит. Умолчание в плейсхолдере — та же
     * константа, что у записи, поэтому без свойства они совпадают.
     *
     * <p>Расходятся они на одном значении — явно пустом: запись берёт умолчание, а
     * {@code @Scheduled} пустую строку отбрасывает и тикает сразу, как до задержки.
     * Закрывается регистрацией задач с {@link Duration} из записи (план, шаг Ш14b).
     */
    public static final String INITIAL_DELAY_PLACEHOLDER =
            "${projectx.import.initial-delay:" + INITIAL_DELAY_DEFAULT + "}";
}
