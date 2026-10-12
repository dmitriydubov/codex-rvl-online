package ru.projectx.datareference.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.onec.contract")
public record OneCContractProperties(
        @Nullable String rvlActivityId,
        @Nullable String rvlContractProperty,
        @Nullable String dmsProductType,
        @DefaultValue("Адрес") String addressRecordType,
        @DefaultValue({"Фактический адрес контрагента",
                       "Адрес местонахождения (регистрации) контрагента юр.лица"})
        List<String> addressKindPriority) {

    public Optional<String> rvlActivity() {
        return filled(rvlActivityId);
    }

    public Optional<String> rvlContract() {
        return filled(rvlContractProperty);
    }

    public Optional<String> dmsProduct() {
        return filled(dmsProductType);
    }

    private static Optional<String> filled(@Nullable String setting) {
        return Optional.ofNullable(setting).filter(Predicate.not(String::isBlank));
    }
}
