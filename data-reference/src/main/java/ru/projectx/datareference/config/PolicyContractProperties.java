package ru.projectx.datareference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.onec.policy")
public record PolicyContractProperties(
        @DefaultValue("Адрес элпочты застрахованного") String emailProperty) {
}
