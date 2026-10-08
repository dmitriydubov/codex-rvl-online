package ru.projectx.datareference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Значение контракта полисов (ТЗ-1), которое задаёт справочник 1С, а не протокол. Умолчание есть, потому что документ
 * называет имя свойства прямо, а в настройке оно для того, чтобы уточнить подпись без выпуска сервиса.
 */
@Validated
@ConfigurationProperties("projectx.onec.policy")
public record PolicyContractProperties(
        // Имя дополнительного свойства полиса с почтой застрахованного. По этой почте человек входит в личный кабинет,
        // поэтому неверное имя означает, что войти не может никто.
        @DefaultValue("Адрес элпочты застрахованного") String emailProperty) {
}
