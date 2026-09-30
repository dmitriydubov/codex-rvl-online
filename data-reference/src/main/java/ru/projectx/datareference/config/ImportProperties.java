package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
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
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("1h") Duration stuckTransferThreshold,
        @DefaultValue("30d") Duration payloadTtl,
        @DefaultValue("500") @Positive int retentionBatchSize) {
}
