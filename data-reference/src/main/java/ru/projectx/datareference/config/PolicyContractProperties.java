package ru.projectx.datareference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Значения контракта ТЗ-1, которые задаёт справочник 1С, а не протокол.
 *
 * <p>Умолчание здесь <b>есть</b> — в отличие от плейсхолдеров ТЗ-3 и ТЗ-5: ТЗ-1 назвало
 * имя свойства прямым текстом, подставлять наугад нечего. В конфигурации оно потому,
 * что подпись свойства может уточниться, а это вопрос значения, а не выпуска новой
 * версии сервиса.
 *
 * @param emailProperty имя свойства в наборе дополнительных свойств полиса, значением
 *                которого приходит адрес электронной почты застрахованного (ТЗ-1, п. 1.4).
 *                Именно этим адресом человек входит в личный кабинет, поэтому неверное имя
 *                означает не «поле пустое», а «войти не может никто»
 */
@Validated
@ConfigurationProperties("projectx.onec.policy")
public record PolicyContractProperties(
        @DefaultValue("Адрес элпочты застрахованного") String emailProperty) {
}
