package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки разбора для сообщений 1С, пришедших раньше своего родителя: сколько сообщение ждёт родителя и сколько
 * GUID родителей называет запись журнала об истёкших ожиданиях.
 *
 * <p>Свойства лежат рядом с остальными настройками разбора, в {@code projectx.import}, а отдельной записью собраны
 * потому, что относятся к одному правилу. Нулевой или отрицательный срок и предел не пускают сервис в работу.
 *
 * @param parentWaitTimeout     сколько сообщение ждёт родителя, считая от начала ожидания, прежде чем будет отклонено.
 *                              Сутки покрывают первичную выгрузку, которую 1С присылает многими очередями сразу.
 *                              Срок должен быть заметно меньше срока хранения тела: тело ожидающего сообщения не
 *                              стирается, и при сроке ожидания длиннее тело с персональными данными жило бы дольше
 * @param parentWaitReportLimit сколько GUID родителей, первых по порядку приёма, называет запись журнала об истёкших
 *                              ожиданиях потока. При массовой выгрузке истекают тысячи сообщений, и строка со всеми
 *                              GUID была бы нечитаемой; число истёкших запись называет всегда
 */
@Validated
@ConfigurationProperties("projectx.import")
public record ParentWaitProperties(
        @DefaultValue("24h") @DurationMin(millis = 1) Duration parentWaitTimeout,
        @DefaultValue("20") @Positive int parentWaitReportLimit) {
}
