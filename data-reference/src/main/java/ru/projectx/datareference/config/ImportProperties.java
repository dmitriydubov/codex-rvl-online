package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки трёх задач по расписанию, которые работают с таблицей сырых сообщений: разбора сообщений,
 * стирания персональных данных и проверки зависших многочастных передач.
 *
 * <p>Интервалы задаются паузой между проходами: следующий проход начинается через заданное время после
 * окончания предыдущего, поэтому долгий проход не накладывается сам на себя. Нулевая или отрицательная
 * пауза не пускает сервис в работу.
 *
 * @param pollInterval пауза между проходами разбора сообщений
 * @param initialDelay сколько все три задачи ждут после старта до первого прохода. Без неё первый проход
 *                случается прямо на старте, раньше, чем сервис готов к работе. Ноль разрешён и означает
 *                «без задержки»; отрицательное значение не пускает сервис в работу. Явно пустое значение
 *                (например, пустая переменная окружения) даёт умолчание
 * @param batchSize сколько сообщений разбирается за один проход. Первичная загрузка справочников — это
 *                десятки тысяч сообщений, и разбирать их одним проходом значило бы держать в памяти всю
 *                выгрузку сразу
 * @param stuckTransferThreshold сколько многочастная передача прайса может ждать своих частей, прежде чем
 *                это станет поводом для тревоги, больше нуля. Передача, часть которой потерялась у источника, не
 *                соберётся никогда: прайс по её услуге тихо останется вчерашним, и заметить это можно только
 *                по давности
 * @param stuckTransferPollInterval пауза между проверками зависших передач
 * @param stuckTransferReportLimit сколько зависших передач, самых давних, называет одно предупреждение.
 *                Если источник перестал присылать финальные сообщения вовсе, передач могут быть сотни,
 *                и строка со всеми сразу была бы нечитаемой
 * @param payloadTtl сколько тело сообщения с персональными данными живёт в таблице сырья после приёма.
 *                Тридцать суток заметно больше любого разумного срока разбора инцидента и заметно меньше
 *                срока, на котором таблица сырья превращается во вторую копию реестра застрахованных. Меньше
 *                суток не допускается: со сроком около нуля стирались бы и тела ещё не разобранных сообщений,
 *                и разобрать их стало бы нечем
 * @param retentionPollInterval пауза между проходами стирания персональных данных
 * @param retentionBatchSize сколько тел стирается одной транзакцией. Весь остаток одним запросом надолго
 *                заблокировал бы строки, которые в это же время читает разбор; поэтому проход стирает
 *                пачками, и каждая пачка коммитится сразу
 */
@Validated
@ConfigurationProperties("projectx.import")
public record ImportProperties(
        @DefaultValue("10s") @DurationMin(millis = 1) Duration pollInterval,
        @DefaultValue("30s") @DurationMin(millis = 0) Duration initialDelay,
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("1h") @DurationMin(millis = 1) Duration stuckTransferThreshold,
        @DefaultValue("15m") @DurationMin(millis = 1) Duration stuckTransferPollInterval,
        @DefaultValue("20") @Positive int stuckTransferReportLimit,
        @DefaultValue("30d") @DurationMin(days = 1) Duration payloadTtl,
        @DefaultValue("1h") @DurationMin(millis = 1) Duration retentionPollInterval,
        @DefaultValue("500") @Positive int retentionBatchSize) {
}
