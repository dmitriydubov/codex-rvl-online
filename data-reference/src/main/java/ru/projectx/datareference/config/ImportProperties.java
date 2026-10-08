package ru.projectx.datareference.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки задач по расписанию над таблицей сырых сообщений 1С: разбора, стирания персональных данных и проверки
 * зависших передач. Пауза отсчитывается от конца прохода, поэтому долгий проход не накладывается сам на себя.
 * Нулевая или отрицательная пауза, как и любое значение вне пределов, не пускает сервис в работу.
 */
@Validated
@ConfigurationProperties("projectx.import")
public record ImportProperties(
        // На столько, не считая самого прохода, новое сообщение 1С может отстать от справочника.
        @DefaultValue("10s") @DurationMin(millis = 1) Duration pollInterval,
        // Задержка первого прохода всех трёх задач, чтобы он не случился раньше, чем сервис готов к работе.
        // Ноль означает «без задержки», а явно пустое значение, например пустая переменная окружения, даёт умолчание.
        @DefaultValue("30s") @DurationMin(millis = 0) Duration initialDelay,
        // Первичная загрузка справочников — десятки тысяч сообщений, и одним проходом вся выгрузка легла бы в память.
        @DefaultValue("100") @Positive int batchSize,
        // Сколько многочастная передача прайса ждёт своих частей до предупреждения. Передача с потерянной у источника
        // частью не соберётся никогда, и прайс услуги тихо останется вчерашним: заметить это можно только по давности.
        @DefaultValue("1h") @DurationMin(millis = 1) Duration stuckTransferThreshold,
        // На столько, не больше, предупреждение о зависшей передаче опаздывает.
        @DefaultValue("15m") @DurationMin(millis = 1) Duration stuckTransferPollInterval,
        // Сколько самых давних зависших передач называет одно предупреждение: если источник перестал присылать
        // финальные сообщения, их сотни, и строка со всеми сразу была бы нечитаемой.
        @DefaultValue("20") @Positive int stuckTransferReportLimit,
        // Срок тела сообщения с персональными данными: дольше любого разбора инцидента, но сырьё не становится второй
        // копией реестра застрахованных. Меньше суток нельзя: стирались бы тела ещё не разобранных сообщений.
        @DefaultValue("30d") @DurationMin(days = 1) Duration payloadTtl,
        // На столько, не больше, тело с персональными данными может пережить свой срок.
        @DefaultValue("1h") @DurationMin(millis = 1) Duration retentionPollInterval,
        // Сколько тел стирается одной транзакцией: весь остаток одним запросом надолго заблокировал бы строки, которые
        // в это время читает разбор.
        @DefaultValue("500") @Positive int retentionBatchSize,
        // Срок тела карантинного сообщения, которое база не приняла в сырьё. Тело не разобрано и может содержать
        // персональные данные, поэтому срок общий для всех очередей. Меньше суток нельзя: карантин разбирает человек,
        // и ему нужно успеть увидеть тело.
        @DefaultValue("30d") @DurationMin(days = 1) Duration quarantineTtl,
        // Сколько сообщение ждёт родителя, который ещё не пришёл, прежде чем будет отклонено; сутки покрывают первичную
        // выгрузку многими очередями. Срок меньше payloadTtl, потому что тело ожидающего сообщения не стирается.
        @DefaultValue("24h") @DurationMin(millis = 1) Duration parentWaitTimeout,
        // Сколько GUID родителей называет запись журнала об истёкших ожиданиях: при массовой выгрузке их тысячи.
        // Число истёкших запись называет всегда.
        @DefaultValue("20") @Positive int parentWaitReportLimit,
        // Сколько ФИО, дата рождения и почта хранятся в полисе после его окончания или архивации; строка полиса
        // остаётся. Меньше суток нельзя, чтобы опечатка в единицах не стёрла данные сразу. Точный срок согласуется
        // с ответственным за персональные данные.
        @DefaultValue("30d") @DurationMin(days = 1) Duration policyPersonalDataGrace) {

    /** Срок ожидания родителя короче срока хранения тела, иначе тела ожидающих сообщений переживали бы свой срок. */
    @AssertTrue(message = "срок ожидания родителя (parent-wait-timeout) должен быть меньше срока хранения тела "
            + "(payload-ttl)")
    public boolean isParentWaitShorterThanPayloadTtl() {
        return parentWaitTimeout.compareTo(payloadTtl) < 0;
    }
}
