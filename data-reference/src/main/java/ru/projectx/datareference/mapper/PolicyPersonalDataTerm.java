package ru.projectx.datareference.mapper;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.entity.PersonalDataCutoff;

/**
 * Срок хранения персональных данных в полисах: запас после окончания срока действия или перевода в архив и
 * календарный день сервиса, по которому полис действует. Одна граница на момент нужна и разбору полиса, и проходу
 * стирания, и метрике, поэтому считается она в одном месте.
 */
@Component
public class PolicyPersonalDataTerm {

    private final ImportProperties properties;
    private final ReferenceApiProperties api;

    public PolicyPersonalDataTerm(ImportProperties properties, ReferenceApiProperties api) {
        this.properties = properties;
        this.api = api;
    }

    /** Граница хранения на момент {@code now}. */
    public PersonalDataCutoff cutoffAt(Instant now) {
        return PersonalDataCutoff.at(now, properties.policyPersonalDataGrace(), api.zone());
    }
}
