package ru.projectx.datareference.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Приём сообщений из брокера 1С:УСК.
 *
 * @param storageUnavailableBackoff пауза перед возвратом сообщения источнику, когда
 *                до нашей базы не удалось достучаться. Без неё брокер отдаёт сообщение
 *                обратно немедленно, и недоступная база превращается в горячий цикл:
 *                десятки повторов в секунду на очередь, лог, в котором ничего не найти,
 *                и нагрузка на чужой брокер
 */
@Validated
@ConfigurationProperties("projectx.onec.ingest")
public record IngestProperties(@DefaultValue("5s") Duration storageUnavailableBackoff) {
}
