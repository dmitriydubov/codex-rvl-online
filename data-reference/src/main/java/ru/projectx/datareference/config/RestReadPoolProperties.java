package ru.projectx.datareference.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Пул соединений REST-чтений — отдельный от пула, которым пользуются приём сообщений 1С:УСК, разбор и задачи по
 * расписанию. Всплеск запросов соседей занимает только его, и приёму с разбором соединения остаются всегда.
 *
 * <p>Сетевой тайм-аут чтения здесь короткий, в целых секундах: он обрывает чтение, когда база зависла и не отвечает
 * вовсе. Тогда лимит REST-чтения не срабатывает: его отмена уходит в базу отдельным соединением, а зависшая база её
 * не исполняет. У пула приложения этот тайм-аут длинный, потому что разбор ведёт долгие операции.
 *
 * <p>Отмену по лимиту REST-чтения драйвер шлёт отдельным соединением и, прежде чем вернуть ошибку, ждёт, пока она
 * уйдёт. Зависшая база её не примет, и без своего предела это ожидание (у драйвера по умолчанию 10 с) прибавилось бы
 * к ответу. Лимит чтения вместе с этим пределом не должен превышать сетевой тайм-аут.
 *
 * @param maximumPoolSize            сколько соединений держит пул; столько REST-чтений идут к базе одновременно
 * @param connectionTimeout          сколько ждать свободного соединения, прежде чем ответить 503; меньше 250 мс
 *                                   пул не принимает
 * @param socketTimeoutSeconds       сетевой тайм-аут чтения драйвера PostgreSQL в целых секундах
 * @param cancelSignalTimeoutSeconds сколько секунд драйвер ждёт отправки отмены запроса
 */
@Validated
@ConfigurationProperties("projectx.database.rest-read-pool")
public record RestReadPoolProperties(
        @Min(1) int maximumPoolSize,
        @NotNull @DurationMin(millis = 250) Duration connectionTimeout,
        @Min(1) int socketTimeoutSeconds,
        @Min(1) int cancelSignalTimeoutSeconds) {
}
