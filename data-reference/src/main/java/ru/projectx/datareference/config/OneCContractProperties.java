package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotBlank;
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
        @DefaultValue({"ЛичноеСтрахование", "Личное страхование"}) List<String> dmsProductType,
        @DefaultValue("Страхователь") @NotBlank String insurerRole,
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

    public List<String> dmsProducts() {
        return dmsProductType.stream().flatMap(product -> filled(product).stream()).toList();
    }

    private static Optional<String> filled(@Nullable String setting) {
        return Optional.ofNullable(setting).filter(Predicate.not(String::isBlank));
    }
}
